package cn.zencode.ide;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.e4.ui.css.swt.theme.IThemeManager;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IViewReference;
import org.osgi.service.prefs.BackingStoreException;

public final class ThemeController {
    private ThemeController() { }
    public static boolean isDark() {
        return !"light".equals(InstanceScope.INSTANCE.getNode("cn.zencode.ide").get("appearance", "dark"));
    }
    public static void apply(boolean dark) {
        IThemeEngine engine = PlatformUI.getWorkbench().getService(IThemeEngine.class);
        if (engine == null) {
            IThemeManager manager = PlatformUI.getWorkbench().getService(IThemeManager.class);
            if (manager != null) engine = manager.getEngineForDisplay(PlatformUI.getWorkbench().getDisplay());
        }
        if (engine == null) throw new IllegalStateException("主题服务尚未就绪，请稍后重试。");
        engine.setTheme(dark ? "org.eclipse.e4.ui.css.theme.e4_dark" : "org.eclipse.e4.ui.css.theme.e4_default", true);
        IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode("cn.zencode.ide");
        preferences.put("appearance", dark ? "dark" : "light");
        String background = dark ? "30,34,39" : "250,252,250";
        String foreground = dark ? "220,230,222" : "28,40,32";
        IEclipsePreferences editor = InstanceScope.INSTANCE.getNode("org.eclipse.ui.editors");
        for (String role : new String[] {"Background", "Foreground", "SelectionBackground", "SelectionForeground"}) {
            editor.putBoolean("AbstractTextEditor.Color." + role + ".SystemDefault", false);
        }
        editor.put("AbstractTextEditor.Color.Background", background);
        editor.put("AbstractTextEditor.Color.Foreground", foreground);
        editor.put("AbstractTextEditor.Color.SelectionBackground", dark ? "48,78,66" : "190,220,200");
        editor.put("AbstractTextEditor.Color.SelectionForeground", dark ? "255,255,255" : "15,35,22");
        editor.put("currentLineColor", dark ? "42,49,45" : "233,242,236");
        editor.put("lineNumberColor", dark ? "155,170,160" : "83,105,91");
        IEclipsePreferences java = InstanceScope.INSTANCE.getNode("org.eclipse.jdt.ui");
        for (String key : new String[] {"java_default", "java_operator", "java_bracket"}) java.put(key, foreground);
        for (String key : new String[] {"java_keyword", "java_keyword_return"}) java.put(key, dark ? "126,231,166" : "20,105,55");
        java.put("java_string", dark ? "230,199,136" : "130,65,20");
        for (String key : new String[] {"java_single_line_comment", "java_multi_line_comment", "java_doc_default"}) java.put(key, dark ? "160,179,166" : "82,111,91");
        IEclipsePreferences console = InstanceScope.INSTANCE.getNode("org.eclipse.debug.ui");
        console.put("consoleBackground", background); console.put("outColor", foreground);
        console.put("errorColor", dark ? "255,150,150" : "175,30,30");
        for (IEclipsePreferences node : new IEclipsePreferences[] {preferences, editor, java, console}) {
            try { node.flush(); } catch (BackingStoreException e) { throw new IllegalStateException("主题已切换，但设置保存失败。", e); }
        }
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                for (IViewReference reference : page.getViewReferences()) {
                    if (reference.getView(false) instanceof AssistantView) ((AssistantView) reference.getView(false)).applyTheme(dark);
                }
            }
        }
    }
}
