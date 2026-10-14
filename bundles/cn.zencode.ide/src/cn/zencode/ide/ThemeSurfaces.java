package cn.zencode.ide;

import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.custom.SashForm;

/** Theme transient shells as well as the persistent workbench. UI thread only. */
final class ThemeSurfaces {
    private static boolean installed;
    private static boolean dark;
    private static IThemeEngine engine;
    private static final String GRID = "cn.zencode.originalGridLines";

    static void apply(IThemeEngine themeEngine, boolean useDark) {
        engine = themeEngine;
        dark = useDark;
        Display display = Display.getCurrent();
        JFaceResources.getColorRegistry().put("cn.zencode.surface", dark ? new RGB(30, 34, 39) : new RGB(250, 252, 250));
        JFaceResources.getColorRegistry().put("cn.zencode.outline", dark ? new RGB(65, 83, 73) : new RGB(171, 190, 177));
        if (!installed) {
            installed = true;
            // Views (including Problems) may be created after their Shell was shown.
            display.addFilter(SWT.Paint, event -> {
                if (event.widget instanceof Table || event.widget instanceof Tree) grid((Control) event.widget);
            });
            display.addFilter(SWT.Show, event -> {
                if (event.widget instanceof Shell) {
                    Shell shell = (Shell) event.widget;
                    // Creation of popup children may complete after the Show notification.
                    display.asyncExec(() -> { if (!shell.isDisposed()) style(shell); });
                }
            });
        }
        if ("win32".equals(SWT.getPlatform())) {
            Color outline = JFaceResources.getColorRegistry().get("cn.zencode.outline");
            display.setData("org.eclipse.swt.internal.win32.Table.headerLineColor", outline);
        }
        for (Shell shell : display.getShells()) style(shell);
    }

    private static void style(Shell shell) {
        engine.applyStyles(shell, true);
        shell.setBackground(JFaceResources.getColorRegistry().get("cn.zencode.surface"));
        tables(shell);
        shell.redraw();
    }

    private static void grid(Control control) {
        if (!(control instanceof Table) && !(control instanceof Tree)) return;
        boolean visible = control instanceof Table ? ((Table) control).getLinesVisible() : ((Tree) control).getLinesVisible();
        if (control.getData(GRID) == null) control.setData(GRID, Boolean.valueOf(visible));
        boolean wanted = !dark && Boolean.TRUE.equals(control.getData(GRID));
        if (visible != wanted) {
            if (control instanceof Table) ((Table) control).setLinesVisible(wanted);
            else ((Tree) control).setLinesVisible(wanted);
        }
    }

    private static void tables(Composite parent) {
        for (Control child : parent.getChildren()) {
            grid(child);
            if (child instanceof Sash || child instanceof SashForm)
                child.setBackground(JFaceResources.getColorRegistry().get("cn.zencode.outline"));
            if (child instanceof Composite) tables((Composite) child);
        }
    }
}
