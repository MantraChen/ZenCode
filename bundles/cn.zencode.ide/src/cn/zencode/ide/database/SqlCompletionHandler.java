package cn.zencode.ide.database;

import java.util.List;
import org.eclipse.core.commands.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import cn.zencode.assist.core.database.SqlCompletion;

/** Explicit editor completion using public workbench and JFace document APIs. */
public final class SqlCompletionHandler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) {
        Shell shell=HandlerUtil.getActiveShell(event);
        IEditorPart part=HandlerUtil.getActiveEditor(event);
        ITextEditor editor=part instanceof ITextEditor?(ITextEditor)part:part==null?null:part.getAdapter(ITextEditor.class);
        if(editor==null || editor.getDocumentProvider()==null) { MessageDialog.openInformation(shell,"SQL 元数据补全","请先打开 SQL、Java 或 JSP 文本编辑器。"); return null; }
        IDocument document=editor.getDocumentProvider().getDocument(editor.getEditorInput());
        ISelection selection=editor.getSelectionProvider().getSelection();
        if(document==null || !(selection instanceof ITextSelection)) return null;
        ITextSelection text=(ITextSelection)selection;
        if(text.getLength()!=0) { MessageDialog.openInformation(shell,"SQL 元数据补全","请将光标放在要补全的标识符末尾，取消文本选择。"); return null; }
        String name=editor.getEditorInput().getName(); int dot=name.lastIndexOf('.');
        String before=document.get();
        SqlCompletion.Context context=SqlCompletion.context(before,text.getOffset(),dot<0?"":name.substring(dot+1));
        if(context==null) { MessageDialog.openInformation(shell,"SQL 元数据补全","当前位置不是 SQL 标识符上下文。Java / JSP 仅支持以 SQL 关键字开头的字符串。"); return null; }
        List<String> candidates=SqlCompletion.candidates(DatabaseMetadataSession.snapshot(),context);
        if(candidates.isEmpty()) { MessageDialog.openInformation(shell,"SQL 元数据补全","当前会话没有匹配的表列候选。请先通过 JDBC 连接读取元数据；候选仅来自该快照。"); return null; }
        ListDialog dialog=new ListDialog(shell); dialog.setTitle("SQL 元数据补全"); dialog.setMessage("选择表名或列名插入编辑器（不会执行 SQL）");
        dialog.setContentProvider(ArrayContentProvider.getInstance()); dialog.setLabelProvider(new LabelProvider()); dialog.setInput(candidates);
        if(dialog.open()!=org.eclipse.jface.window.Window.OK || dialog.getResult()==null || dialog.getResult().length==0) return null;
        if(!before.equals(document.get())) { MessageDialog.openWarning(shell,"源文本已改变","补全已取消，请在当前文本上重试。"); return null; }
        if(editor instanceof org.eclipse.ui.texteditor.ITextEditorExtension2 && !((org.eclipse.ui.texteditor.ITextEditorExtension2)editor).validateEditorInputState()) return null;
        if(!before.equals(document.get())) { MessageDialog.openWarning(shell,"源文本已改变","补全已取消，请在当前文本上重试。"); return null; }
        String replacement=(String)dialog.getResult()[0];
        try { document.replace(context.start,text.getOffset()-context.start,replacement); editor.selectAndReveal(context.start+replacement.length(),0); }
        catch(BadLocationException changed) { MessageDialog.openWarning(shell,"补全已取消","文档位置已改变，请重试。"); }
        return null;
    }
}
