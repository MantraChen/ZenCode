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
        org.eclipse.jface.preference.IPreferenceStore java = org.eclipse.jdt.ui.PreferenceConstants.getPreferenceStore();
        for (String key : new String[] {"java_default", "java_operator", "java_bracket"}) java.setValue(key, foreground);
        for (String key : new String[] {"java_keyword", "java_keyword_return"}) java.setValue(key, dark ? "126,231,166" : "20,105,55");
        java.setValue("java_string", dark ? "230,199,136" : "130,65,20");
        for (String key : new String[] {"java_single_line_comment", "java_multi_line_comment", "java_doc_default"}) java.setValue(key, dark ? "160,179,166" : "82,111,91");
        // Windows font linking can fail for synthesized italic CJK glyphs.
        for (String key : new String[] {"java_single_line_comment", "java_multi_line_comment",
                "java_doc_default", "java_doc_keyword", "java_doc_tag", "java_doc_link", "java_comment_task_tag"}) {
            java.setValue(key + "_italic", false);
            java.setValue(key + "_bold", false);
        }
        // JDT caches token colors in its preference store; notify that store directly.
        java.setValue("java_doc_keyword", dark ? "160,179,166" : "70,96,80");
        java.setValue("java_doc_tag", dark ? "130,194,226" : "0,85,130");
        java.setValue("java_doc_link", dark ? "130,194,226" : "0,85,130");
        java.setValue("java_comment_task_tag", dark ? "230,199,136" : "130,65,20");
        for (String role : new String[] {"abstractClass", "abstractMethodInvocation", "annotation",
                "annotationElementReference", "class", "deprecatedMember", "enum", "field", "inheritedField",
                "inheritedMethodInvocation", "interface", "localVariable", "localVariableDeclaration", "method",
                "methodDeclarationName", "number", "parameterVariable", "staticField", "staticFinalField",
                "staticMethodInvocation", "typeArgument", "typeParameter", "varKeyword", "yieldKeyword", "recordKeyword"}) {
            java.setValue("semanticHighlighting." + role + ".color", dark ? "160,210,185" : "35,80,58");
        }
        for (String popup : new String[] {"content_assist_parameters", "content_assist_proposals"}) {
            java.setValue(popup + "_background", background);
            java.setValue(popup + "_foreground", foreground);
        }
        java.setValue("matchingBracketsColor", dark ? "200,220,210" : "65,85,72");
        applyNativeMenu(dark);
        ThemeSurfaces.apply(engine, dark);
        IEclipsePreferences console = InstanceScope.INSTANCE.getNode("org.eclipse.debug.ui");
        console.put("consoleBackground", background); console.put("outColor", foreground);
        console.put("errorColor", dark ? "255,150,150" : "175,30,30");
        for (IEclipsePreferences node : new IEclipsePreferences[] {preferences, editor, console}) {
            try { node.flush(); } catch (BackingStoreException e) { throw new IllegalStateException("主题已切换，但设置保存失败。", e); }
        }
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            for (IWorkbenchPage page : window.getPages()) {
                for (org.eclipse.ui.IEditorReference reference : page.getEditorReferences()) {
                    org.eclipse.ui.IEditorPart part = reference.getEditor(false);
                    if (part != null) {
                        Object viewer = part.getAdapter(org.eclipse.jface.text.ITextOperationTarget.class);
                        if (viewer instanceof org.eclipse.jface.text.ITextViewer)
                            ((org.eclipse.jface.text.ITextViewer) viewer).invalidateTextPresentation();
                    }
                }
                for (IViewReference reference : page.getViewReferences()) {
                    if (reference.getView(false) instanceof AssistantView) ((AssistantView) reference.getView(false)).applyTheme(dark);
                }
            }
        }
    }
    private static void applyNativeMenu(boolean dark) {
        if (!"win32".equals(org.eclipse.swt.SWT.getPlatform())) return;
        org.eclipse.swt.widgets.Display display = PlatformUI.getWorkbench().getDisplay();
        // SWT 3.114's native menu bar is outside CSS. These version-specific data keys
        // copy COLORREF values immediately; no internal SWT classes or native calls are used.
        org.eclipse.swt.graphics.Color bg = new org.eclipse.swt.graphics.Color(display, dark ? 47 : 240, dark ? 47 : 240, dark ? 47 : 240);
        org.eclipse.swt.graphics.Color fg = new org.eclipse.swt.graphics.Color(display, dark ? 238 : 28, dark ? 238 : 40, dark ? 238 : 32);
        try {
            display.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor", fg);
            display.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor", bg);
            display.setData("org.eclipse.swt.internal.win32.menuBarBorderColor", bg);
            for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                org.eclipse.swt.widgets.Shell shell = window.getShell();
                org.eclipse.swt.widgets.Menu menu = shell.getMenuBar();
                if (menu != null) { shell.setMenuBar(null); shell.setMenuBar(menu); }
                shell.redraw();
            }
        } finally { bg.dispose(); fg.dispose(); }
    }

}
