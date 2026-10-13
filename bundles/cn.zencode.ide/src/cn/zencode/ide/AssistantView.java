package cn.zencode.ide;

import cn.zencode.assist.core.Diagnostic;
import cn.zencode.assist.core.JavaPunctuationCheck;
import cn.zencode.assist.core.TextFix;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

/** First native terminal-styled assistant. Commands are application actions, never shell input. */
public final class AssistantView extends ViewPart {
    private StyledText output;
    private Text command;
    private Color background, foreground, accent, warning, error;
    private Font mono;
    private final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    private IDocument checkedDocument;

    @Override public void createPartControl(Composite parent) {
        background = new Color(parent.getDisplay(), 13, 17, 23);
        foreground = new Color(parent.getDisplay(), 210, 222, 214);
        accent = new Color(parent.getDisplay(), 126, 231, 166);
        warning = new Color(parent.getDisplay(), 230, 199, 136);
        error = new Color(parent.getDisplay(), 255, 145, 145);
        mono = new Font(parent.getDisplay(), "Consolas", 11, SWT.NORMAL);
        parent.setLayout(new GridLayout(1, false));
        parent.setBackground(background);
        Label title = new Label(parent, SWT.NONE);
        title.setText("ZENCODE / OFFLINE ASSIST"); title.setForeground(accent); title.setBackground(background); title.setFont(mono);
        Button check = new Button(parent, SWT.PUSH);
        check.setText("[ CHECK ] 检查当前 Java 文件");
        check.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        check.addSelectionListener(new SelectionAdapter() { @Override public void widgetSelected(SelectionEvent e) { checkEditor(); } });
        output = new StyledText(parent, SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
        output.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        output.setBackground(background); output.setForeground(foreground); output.setFont(mono);
        command = new Text(parent, SWT.SINGLE | SWT.BORDER);
        command.setMessage("> help / check / preview 1 / fix 1 / clear");
        command.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        command.setFont(mono);
        command.addSelectionListener(new SelectionAdapter() { @Override public void widgetDefaultSelected(SelectionEvent e) {
            String value = command.getText().trim(); command.setText(""); dispatch(value);
        }});
        help();
    }
    private void help() {
        output.setText("[ READY ] 离线排错核心已加载\n\n> check\n检查当前 .java 文件中的全角语法标点。\n\n> preview 1 / fix 1\n预览第 1 条问题的修改；确认后应用。每次修复后重新检查。\n\n方法名拼写错误：在 Java 编辑器中使用 Ctrl+1，查看带 [ZenCode] 的候选。\n\n当前为原型：JSP、跨文件诊断、数据库助手尚未实现。\n命令栏只执行以上应用命令，不执行系统命令。\n");
    }
    private void dispatch(String value) {
        if ("help".equals(value) || "帮助".equals(value)) { help(); return; }
        if ("check".equals(value) || "检查".equals(value)) { checkEditor(); return; }
        if ("clear".equals(value)) { output.setText(""); return; }
        if (value.matches("(preview|fix) [1-9][0-9]{0,3}")) {
            int index = Integer.parseInt(value.substring(value.indexOf(' ') + 1)) - 1;
            if (index >= diagnostics.size() || checkedDocument == null) { append("[ WARN ] 没有该条诊断，请先 check。"); return; }
            TextFix fix = diagnostics.get(index).getFix();
            if (!fix.isCurrent(checkedDocument.get())) { append("[ WARN ] 文件已变化，请重新 check。"); return; }
            if (getCurrentDocument() != checkedDocument) { append("[ WARN ] 当前编辑器已经改变，请返回原文件或重新 check。"); return; }
            String before = checkedDocument.get().substring(fix.getOffset(), fix.getOffset() + fix.getLength());
            String preview = "第 " + diagnostics.get(index).getLine() + " 行：\n「" + before + "」 → 「" + fix.getReplacement() + "」";
            append("[ PREVIEW ] " + preview);
            if (value.startsWith("fix ") && MessageDialog.openConfirm(getSite().getShell(), "ZenCode · 应用修改", preview + "\n\n应用后可在编辑器中撤销。")) {
                if (!fix.isCurrent(checkedDocument.get())) { append("[ WARN ] 文件已变化，请重新检查。"); return; }
                try { checkedDocument.replace(fix.getOffset(), fix.getLength(), fix.getReplacement()); checkEditor(); }
                catch (BadLocationException ex) { append("[ ERROR ] 编辑位置失效，请重新检查。"); }
            }
            return;
        }
        append("[ WARN ] 未知命令。输入 help 查看可用操作。");
    }
    private IDocument getCurrentDocument() {
        IEditorPart part = getSite().getPage().getActiveEditor();
        if (!(part instanceof ITextEditor) || !part.getEditorInput().getName().endsWith(".java")) return null;
        ITextEditor editor = (ITextEditor) part;
        return editor.getDocumentProvider().getDocument(editor.getEditorInput());
    }
    private void checkEditor() {
        diagnostics.clear(); checkedDocument = getCurrentDocument();
        if (checkedDocument == null) { append("[ INFO ] 请先打开一个 .java 文件。此检查器不解析 JSP/JS。"); return; }
        String source = checkedDocument.get();
        if (source.length() > 200000) { append("[ WARN ] 此原型仅检查 200,000 字符以内的教学文件；大文件请使用 JDT 诊断。"); return; }
        if (source.contains("\\u")) { append("[ INFO ] 文件包含 Unicode 转义，已交由 JDT 处理，避免标点误判。"); return; }
        diagnostics.addAll(new JavaPunctuationCheck().check(source));
        output.setText("[ CHECK ] Java 标点检查：" + diagnostics.size() + " 条建议\n这不是完整编译结果；请同时查看 Problems 面板。\n");
        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic d = diagnostics.get(i);
            append("[ WARN " + (i + 1) + " ] L" + d.getLine() + " " + d.getMessage());
        }
    }
    private void append(String text) {
        int start = output.getCharCount() + 1;
        output.append("\n" + text + "\n");
        int end = text.indexOf(']');
        if (end >= 0) {
            Color color = text.startsWith("[ ERROR") ? error : text.startsWith("[ WARN") ? warning : accent;
            output.setStyleRange(new StyleRange(start, end + 1, color, background));
        }
        output.setTopIndex(output.getLineCount() - 1);
    }
    @Override public void setFocus() { command.setFocus(); }
    @Override public void dispose() {
        if (mono != null) mono.dispose();
        if (background != null) background.dispose();
        if (foreground != null) foreground.dispose();
        if (accent != null) accent.dispose();
        if (warning != null) warning.dispose();
        if (error != null) error.dispose();
        super.dispose();
    }
}
