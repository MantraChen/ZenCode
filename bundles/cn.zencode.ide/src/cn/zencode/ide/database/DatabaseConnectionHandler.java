package cn.zencode.ide.database;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.util.Properties;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.handlers.HandlerUtil;
import cn.zencode.assist.core.database.DriverCompatibility;
import cn.zencode.assist.core.database.DatabaseCatalog;

/** Explicit, session-only connection test. No SQL is executed. */
public final class DatabaseConnectionHandler extends AbstractHandler {
    private static final java.util.concurrent.atomic.AtomicBoolean DRIVER_BUSY = new java.util.concurrent.atomic.AtomicBoolean();
    @Override public Object execute(ExecutionEvent event) {
        new ConnectionDialog(HandlerUtil.getActiveShell(event)).open();
        return null;
    }
    private static final class ConnectionDialog extends Dialog {
        private Text jar, driver, url, user, password;
        private Label result;
        private Table metadata;
        private Text filter;
        private java.util.List<DatabaseCatalog.Entry> snapshot = java.util.Collections.emptyList();
        private String[] paths = new String[0];
        private Button test;
        private volatile Job running;
        ConnectionDialog(Shell shell) { super(shell); }
        @Override protected void configureShell(Shell shell) { super.configureShell(shell); shell.setText("离线 JDBC 连接测试"); }
        private Text field(Composite parent, String label, int style) {
            new Label(parent, SWT.NONE).setText(label);
            Text text = new Text(parent, SWT.BORDER | style);
            text.setLayoutData(new GridData(420, SWT.DEFAULT)); return text;
        }
        @Override protected Control createDialogArea(Composite parent) {
            Composite body = new Composite((Composite) super.createDialogArea(parent), SWT.NONE);
            body.setLayout(new GridLayout(2, false));
            new Label(body, SWT.NONE).setText("数据库模板");
            Combo templates = new Combo(body, SWT.READ_ONLY); templates.setItems(DatabaseCatalog.NAMES); templates.select(0);
            templates.addListener(SWT.Selection, e -> { driver.setText(DatabaseCatalog.driver(templates.getSelectionIndex())); url.setText(DatabaseCatalog.url(templates.getSelectionIndex())); });
            jar = field(body, "本地驱动 JAR（可多选）", SWT.READ_ONLY);
            new Label(body, SWT.NONE);
            Button browse = new Button(body, SWT.PUSH); browse.setText("选择可信驱动…");
            browse.addListener(SWT.Selection, e -> { FileDialog fd = new FileDialog(getShell(), SWT.OPEN | SWT.MULTI); fd.setFilterExtensions(new String[]{"*.jar"}); String p = fd.open(); if (p != null) { String[] names = fd.getFileNames(); paths = new String[names.length]; for (int i = 0; i < names.length; i++) paths[i] = new File(fd.getFilterPath(), names[i]).getPath(); jar.setText(String.join("; ", paths)); } });
            driver = field(body, "驱动类全名", SWT.NONE);
            url = field(body, "JDBC URL", SWT.NONE); user = field(body, "用户名", SWT.NONE);
            password = field(body, "密码（仅本次使用）", SWT.PASSWORD);
            Label help = new Label(body, SWT.WRAP); help.setText("仅加载您信任的本地驱动；不下载依赖、不执行 SQL。\n请按实际数据库文档填写驱动类和 URL。\n应用查询请使用 PreparedStatement 和参数绑定。");
            GridData hd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1); hd.widthHint = 520; help.setLayoutData(hd);
            test = new Button(body, SWT.PUSH); test.setText("连接并读取元数据（15 秒）"); test.addListener(SWT.Selection, e -> start());
            Button cancel = new Button(body, SWT.PUSH); cancel.setText("取消测试"); cancel.addListener(SWT.Selection, e -> { if (running != null) running.cancel(); });
            result = new Label(body, SWT.WRAP); result.setText("等待测试"); result.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
            filter = field(body, "离线候选前缀", SWT.NONE);
            filter.addModifyListener(e -> refresh());
            metadata = new Table(body, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL); metadata.setHeaderVisible(true); metadata.setLinesVisible(true);
            for (String title : new String[]{"表 / 视图", "列名", "类型"}) { TableColumn col = new TableColumn(metadata, SWT.NONE); col.setText(title); col.setWidth(175); }
            GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1); tableData.heightHint = 170; metadata.setLayoutData(tableData);
            Button copy = new Button(body, SWT.PUSH); copy.setText("复制选中候选"); copy.addListener(SWT.Selection, e -> { if (metadata.getSelectionCount() > 0) { DatabaseCatalog.Entry item = (DatabaseCatalog.Entry) metadata.getSelection()[0].getData(); copy(item.candidate()); } });
            Button sample = new Button(body, SWT.PUSH); sample.setText("复制 PreparedStatement 示例"); sample.addListener(SWT.Selection, e -> copy(DatabaseCatalog.preparedExample()));
            Text example = new Text(body, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL); example.setText(DatabaseCatalog.preparedExample());
            GridData exampleData = new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1); exampleData.heightHint = 100; example.setLayoutData(exampleData);
            return body;
        }
        private void copy(String value) {
            org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(getShell().getDisplay());
            try { clipboard.setContents(new Object[]{value}, new org.eclipse.swt.dnd.Transfer[]{org.eclipse.swt.dnd.TextTransfer.getInstance()}); } finally { clipboard.dispose(); }
        }
        private void refresh() {
            if (metadata == null || metadata.isDisposed()) return;
            metadata.removeAll();
            for (DatabaseCatalog.Entry entry : DatabaseCatalog.candidates(snapshot, filter.getText())) { TableItem item = new TableItem(metadata, SWT.NONE); item.setText(new String[]{entry.table, entry.column, entry.type == null ? "" : entry.type}); item.setData(entry); }
        }
        private void start() {
            final String[] selectedPaths = paths.clone();
            final String clazz = driver.getText().trim(), address = url.getText().trim();
            if (selectedPaths.length == 0 || clazz.isEmpty() || !address.startsWith("jdbc:")) { result.setText("请选择本地 JAR、填写驱动类及 jdbc: URL。"); return; }
            if (!DRIVER_BUSY.compareAndSet(false, true)) { result.setText("上次驱动调用仍未退出，请等待或重新启动应用后再试。"); return; }
            final Properties credentials = new Properties(); credentials.setProperty("user", user.getText()); credentials.setProperty("password", password.getText()); password.setText("");
            snapshot = java.util.Collections.emptyList(); DatabaseMetadataSession.clear(); refresh();
            test.setEnabled(false); result.setText("正在连接；可取消。驱动代码可能无法被中断。");
            final Display display = getShell().getDisplay();
            running = new Job("JDBC 连接测试") {
                @Override protected IStatus run(IProgressMonitor monitor) {
                    final java.util.concurrent.atomic.AtomicReference<java.util.List<DatabaseCatalog.Entry>> loaded = new java.util.concurrent.atomic.AtomicReference<java.util.List<DatabaseCatalog.Entry>>();
                    FutureTask<String> attempt = new FutureTask<String>(() -> {
                        try {
                            URL[] locations = new URL[selectedPaths.length];
                            for (int i = 0; i < selectedPaths.length; i++) { DriverCompatibility.requireJava8(selectedPaths[i]); locations[i] = new File(selectedPaths[i]).toURI().toURL(); }
                            try (URLClassLoader loader = new URLClassLoader(locations, Driver.class.getClassLoader())) {
                                Object instance = Class.forName(clazz, true, loader).getDeclaredConstructor().newInstance();
                                if (!(instance instanceof Driver)) return "所填类未实现 java.sql.Driver。";
                                Driver jdbc = (Driver) instance;
                                try (Connection connection = jdbc.connect(address, credentials)) {
                                    if (connection == null) return "驱动不接受此 JDBC URL，请核对数据库类型。";
                                    loaded.set(DatabaseCatalog.read(connection));
                                    return "连接成功；已读取表列元数据并关闭连接。最多显示100个表/视图，每表200列；候选可复制或通过 SQL 元数据补全插入编辑器，未执行应用 SQL。";
                                }
                            }
                        } catch (Throwable failure) {
                            return cn.zencode.assist.core.database.DriverDiagnostics.describe(failure);
                        } finally { credentials.clear(); DRIVER_BUSY.set(false); }
                    });
                    Thread worker = new Thread(attempt, "zencode-jdbc-test"); worker.setDaemon(true); worker.start();
                    boolean completed = false;
                    String message = "连接超时；已停止等待。驱动若稍后返回，将关闭连接。";
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                    try {
                        while (System.nanoTime() < deadline) {
                            if (monitor.isCanceled()) { message = "已取消等待；驱动若稍后返回，将关闭连接。"; break; }
                            try { message = attempt.get(100, TimeUnit.MILLISECONDS); completed = true; break; } catch (TimeoutException waiting) { /* bounded polling */ }
                        }
                    } catch (Exception failure) { message = "测试已结束，请重新检查连接配置。"; }
                    final String shown = message;
                    final java.util.List<DatabaseCatalog.Entry> captured = completed ? loaded.get() : null;
                    if (!display.isDisposed()) display.asyncExec(() -> { if (result.isDisposed()) return; if (captured != null) { snapshot = captured; DatabaseMetadataSession.publish(captured); refresh(); } result.setText(shown); test.setEnabled(true); running = null; result.getParent().layout(); });
                    return Status.OK_STATUS;
                }
            };
            running.schedule();
        }
        @Override public boolean close() { if (running != null) running.cancel(); return super.close(); }
    }
}
