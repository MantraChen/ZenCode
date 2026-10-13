package cn.zencode.ide;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public final class TerminalPerspective implements IPerspectiveFactory {
    @Override public void createInitialLayout(IPageLayout layout) {
        String editor = layout.getEditorArea();
        layout.addView("org.eclipse.ui.navigator.ProjectExplorer", IPageLayout.LEFT, 0.20f, editor);
        layout.addView("cn.zencode.ide.assistant", IPageLayout.RIGHT, 0.72f, editor);
        IFolderLayout bottom = layout.createFolder("output", IPageLayout.BOTTOM, 0.72f, editor);
        bottom.addView("org.eclipse.ui.console.ConsoleView");
        bottom.addView(IPageLayout.ID_PROBLEM_VIEW);
        layout.addShowViewShortcut("cn.zencode.ide.assistant");
        layout.addPerspectiveShortcut("org.eclipse.jdt.ui.JavaPerspective");
        layout.addPerspectiveShortcut("org.eclipse.debug.ui.DebugPerspective");
        layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.JavaProjectWizard");
        layout.addNewWizardShortcut("org.eclipse.jdt.ui.wizards.NewClassCreationWizard");
        layout.addActionSet("org.eclipse.debug.ui.launchActionSet");
    }
}
