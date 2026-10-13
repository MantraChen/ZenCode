package cn.zencode.ide;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ui.statushandlers.StatusManager;

public final class ThemeStartup implements IStartup {
    @Override public void earlyStartup() {
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            if (PlatformUI.getWorkbench().isClosing()) return;
            try { ThemeController.apply(ThemeController.isDark()); }
            catch (RuntimeException e) {
                StatusManager.getManager().handle(new Status(IStatus.ERROR, "cn.zencode.ide", "无法恢复界面主题", e), StatusManager.LOG);
            }
        });
    }
}
