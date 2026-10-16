package cn.zencode.ide.web;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.wizards.IWizardDescriptor;

/** Uses the installed WTP wizard so facets, deployment and Java paths stay consistent. */
public final class WebProjectCommand extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        IWizardDescriptor descriptor = PlatformUI.getWorkbench().getNewWizardRegistry()
                .findWizard("org.eclipse.jst.servlet.ui.project.facet.WebProjectWizard");
        if (descriptor == null) {
            MessageDialog.openError(window.getShell(), "错误 / Web 工程", "未找到 WTP Dynamic Web Project 向导。请检查完整产品组件；运行时不会下载组件。");
            return null;
        }
        try {
            IWizard wizard = descriptor.createWizard();
            ((org.eclipse.ui.INewWizard) wizard).init(window.getWorkbench(), org.eclipse.jface.viewers.StructuredSelection.EMPTY);
            new WizardDialog(window.getShell(), wizard).open();
        } catch (org.eclipse.core.runtime.CoreException e) {
            throw new ExecutionException("无法打开 Web 工程向导：" + e.getMessage(), e);
        }
        return null;
    }
}
