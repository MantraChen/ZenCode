package cn.zencode.ide.web;
import org.eclipse.core.commands.*;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.wst.server.ui.ServerUIUtil;

/** Delegates to WTP's public UI and registered run/debug commands. */
public final class ServerCommand extends AbstractHandler {
 @Override public Object execute(ExecutionEvent event) throws ExecutionException {
  IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
  String command = event.getCommand().getId();
  if (command.endsWith(".configureServer")) {
   ServerUIUtil.showNewServerWizard(window.getShell(), null, null, null);
   return null;
  }
  IHandlerService service = window.getService(IHandlerService.class);
  try { service.executeCommand("org.eclipse.wst.server.launchShortcut." + (command.endsWith(".debugServer") ? "debug" : "run"), null); }
  catch (Exception e) { throw new ExecutionException("请选择 Web 工程并配置本地服务器：" + e.getMessage(), e); }
  return null;
 }
}
