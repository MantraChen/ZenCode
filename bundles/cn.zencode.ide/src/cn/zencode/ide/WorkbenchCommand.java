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
            if (command.endsWith(".dark") || command.endsWith(".light")) ThemeController.apply(command.endsWith(".dark"));
            else {
                AssistantView view = (AssistantView) window.getActivePage().showView("cn.zencode.ide.assistant");
                if (command.endsWith(".help")) view.showHelp();
            }
        } catch (PartInitException | RuntimeException e) { throw new ExecutionException("无法完成操作：" + e.getMessage(), e); }
        return null;
    }
}
