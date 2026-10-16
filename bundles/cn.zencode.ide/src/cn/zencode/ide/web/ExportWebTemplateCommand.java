package cn.zencode.ide.web;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.handlers.HandlerUtil;

public final class ExportWebTemplateCommand extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        org.eclipse.swt.widgets.Shell shell = HandlerUtil.getActiveShellChecked(event);
        DirectoryDialog directory = new DirectoryDialog(shell);
        directory.setText("选择示例导出父目录");
        String parent = directory.open();
        if (parent == null) return null;
        InputDialog name = new InputDialog(shell, "离线 Web 示例", "新建目录名称（已有目录不会覆盖）", "zencode-web-sample",
                value -> value.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") ? null : "使用字母开头的英文、数字、短横线或下划线");
        if (name.open() != Window.OK) return null;
        Path destination = Paths.get(parent).resolve(name.getValue());
        try {
            cn.zencode.assist.core.runtime.OfflineWebTemplate.create(destination);
            MessageDialog.openInformation(shell, "READY / 离线示例", "已导出：" + destination + "\n包含 JSP、Servlet、参数化 JDBC 示例及操作说明。\n请先通过新建 Web 工程向导配置工程，再按 README.txt 导入源码并 Run on Server。驱动需要自行准备。");
        } catch (java.io.IOException e) {
            MessageDialog.openError(shell, "ERROR / 导出失败", "未覆盖已有文件。导出可能留下部分新文件，请检查目标目录。\n" + e.getMessage());
        }
        return null;
    }
}
