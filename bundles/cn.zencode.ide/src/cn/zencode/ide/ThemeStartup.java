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
            try {
                // Do not reinterpret existing projects or override an explicit encoding.
                org.eclipse.core.runtime.preferences.IEclipsePreferences resources =
                        org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE.getNode("org.eclipse.core.resources");
                if (org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot().getProjects().length == 0 &&
                        resources.get("encoding", null) == null) {
                    resources.put("encoding", "UTF-8");
                    resources.flush();
                }
                ThemeController.apply(ThemeController.isDark());
            } catch (org.osgi.service.prefs.BackingStoreException e) {
                StatusManager.getManager().handle(new Status(IStatus.ERROR, "cn.zencode.ide", "无法保存新工作区编码", e), StatusManager.LOG);
            }
            catch (RuntimeException e) {
                StatusManager.getManager().handle(new Status(IStatus.ERROR, "cn.zencode.ide", "无法恢复界面主题", e), StatusManager.LOG);
            }
        });
    }
}
