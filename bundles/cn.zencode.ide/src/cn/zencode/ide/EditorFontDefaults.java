package cn.zencode.ide;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.ui.PlatformUI;
import org.osgi.service.prefs.BackingStoreException;

/** Initialize only once; explicit user fonts remain under user control. */
final class EditorFontDefaults {
    private static final String FONT = "org.eclipse.jdt.ui.editors.textfont";
    private static final String DONE = "javaDefaultFontInitialized.v1";
    private static final String[] KEYS = {FONT,
        "org.eclipse.e4.ui.css.theme.e4_dark." + FONT,
        "org.eclipse.e4.ui.css.theme.e4_default." + FONT};

    static void initialize() throws BackingStoreException {
        IEclipsePreferences own = InstanceScope.INSTANCE.getNode("cn.zencode.ide");
        if (own.getBoolean(DONE, false)) return;
        IEclipsePreferences ui = InstanceScope.INSTANCE.getNode("org.eclipse.ui.workbench");
        // An existing font choice (including one set through Colors and Fonts) wins.
        for (String key : ui.keys()) {
            if (key.endsWith(FONT) && ui.get(key, "").length() > 0) {
                own.putBoolean(DONE, true);
                own.flush();
                return;
            }
        }
        restore();
    }

    static void restore() throws BackingStoreException {
        FontData[] data = PlatformUI.getWorkbench().getDisplay().getSystemFont().getFontData();
        for (FontData font : data) font.setStyle(SWT.NORMAL);
        // Persist both built-in appearances; changing theme must not resurrect a stale font.
        for (String key : KEYS)
            PreferenceConverter.setValue(PlatformUI.getPreferenceStore(), key, data);
        PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getFontRegistry().put(FONT, data);
        JFaceResources.getFontRegistry().put(FONT, data);
        InstanceScope.INSTANCE.getNode("org.eclipse.ui.workbench").flush();
        IEclipsePreferences own = InstanceScope.INSTANCE.getNode("cn.zencode.ide");
        own.putBoolean(DONE, true);
        own.flush();
    }
}
