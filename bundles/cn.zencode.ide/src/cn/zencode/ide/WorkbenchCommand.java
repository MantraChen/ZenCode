package cn.zencode.ide;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;

public final class WorkbenchCommand extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        String command = event.getCommand().getId();
        try {
            if (command.endsWith(".dark") || command.endsWith(".light")) {
                boolean dark = command.endsWith(".dark");
                boolean changed = dark != ThemeController.isDark();
                ThemeController.apply(dark);
                if (changed && "win32".equals(org.eclipse.swt.SWT.getPlatform()) &&
                        org.eclipse.jface.dialogs.MessageDialog.openQuestion(window.getShell(), "完成主题切换",
                        "编辑区主题已切换并保存。当前 Windows 界面组件的原生菜单栏颜色需要重启才能完整更新。\n\n现在重启 ZenCode 吗？如有未保存的文件，程序会提示保存。选择否可稍后手动重启。")) {
                    org.eclipse.ui.PlatformUI.getWorkbench().restart();
                }
            }
            else if (command.endsWith(".fontDefaults")) {
                EditorFontDefaults.restore();
            }
            else {
                AssistantView view = (AssistantView) window.getActivePage().showView("cn.zencode.ide.assistant");
                if (command.endsWith(".help")) view.showHelp();
            }
        } catch (org.osgi.service.prefs.BackingStoreException | PartInitException | RuntimeException e) { throw new ExecutionException("无法完成操作：" + e.getMessage(), e); }
        return null;
    }
}
