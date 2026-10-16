package cn.zencode.ide.web;

import java.io.File;
import java.util.jar.JarFile;
import java.util.Properties;
import java.io.InputStream;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/** Read-only installation inspection; lifecycle and publish operations remain with WTP. */
public final class TomcatCheckCommand extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        DirectoryDialog picker = new DirectoryDialog(window.getShell());
        picker.setText("选择本地 Tomcat 安装目录");
        String selected = picker.open();
        if (selected == null) return null;
        final File home = new File(selected);
        final org.eclipse.swt.widgets.Shell shell = window.getShell();
        final org.eclipse.swt.widgets.Display display = shell.getDisplay();
        org.eclipse.core.runtime.jobs.Job job = new org.eclipse.core.runtime.jobs.Job("检查本地 Tomcat") {
            @Override protected org.eclipse.core.runtime.IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                String message;
                boolean valid;
                try { message = cn.zencode.assist.core.runtime.TomcatInstallation.inspect(home); valid = true; }
                catch (Exception e) { message = "错误 / 无法验证 Tomcat：" + e.getMessage(); valid = false; }
                final String result = message;
                final boolean success = valid;
                display.asyncExec(() -> {
                    if (shell.isDisposed()) return;
                    if (success) {
                        MessageDialog.openInformation(window.getShell(), "Tomcat 本地检查", result + "\n\n下一步：打开服务器视图 → 新建 → 服务器，选择对应 Apache Tomcat 版本和此目录，选择 JDK 8。\n在 Web 项目属性中选择 目标运行环境；右键项目 → 运行方式 → 在服务器上运行。\n由服务器视图发布、启动、停止和调试；控制台保留原始服务器错误。此检查不代表启动或 Windows 兼容性验证。");
                        try { window.getActivePage().showView("org.eclipse.wst.server.ui.ServersView"); }
                        catch (org.eclipse.ui.PartInitException e) { MessageDialog.openError(window.getShell(), "错误 / 服务器", e.getMessage()); }
                    } else MessageDialog.openError(window.getShell(), "Tomcat 本地检查", result);
                });
                return org.eclipse.core.runtime.Status.OK_STATUS;
            }
        };
        job.setUser(true);
        job.schedule();
        return null;
    }

}
