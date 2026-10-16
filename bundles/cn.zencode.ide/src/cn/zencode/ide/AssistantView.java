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
    private Composite container, outputFrame, commandFrame;
    private Label title;
    private Button check;
    private Color background, foreground, accent, warning, error;
    private Font mono;
    private final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    private IDocument checkedDocument;

    @Override public void createPartControl(Composite parent) {
        container = parent;
        background = new Color(parent.getDisplay(), 13, 17, 23);
        foreground = new Color(parent.getDisplay(), 210, 222, 214);
        accent = new Color(parent.getDisplay(), 126, 231, 166);
        warning = new Color(parent.getDisplay(), 230, 199, 136);
        error = new Color(parent.getDisplay(), 255, 145, 145);
        mono = new Font(parent.getDisplay(), "Consolas", 11, SWT.NORMAL);
        parent.setLayout(new GridLayout(1, false));
        parent.setBackground(background);
        title = new Label(parent, SWT.NONE);
        title.setText("ZenCode / 离线排错"); title.setForeground(accent); title.setBackground(background); title.setFont(mono);
        check = new Button(parent, SWT.PUSH);
        check.setText("[ 检查 ] 当前 Java 文件");
        check.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        check.addSelectionListener(new SelectionAdapter() { @Override public void widgetSelected(SelectionEvent e) { checkEditor(); } });
        outputFrame = frame(parent, true);
        output = new StyledText(outputFrame, SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        output.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        output.setBackground(background); output.setForeground(foreground); output.setFont(mono);
        commandFrame = frame(parent, false);
        command = new Text(commandFrame, SWT.SINGLE);
        command.setMessage("> 帮助 / 检查 / 预览 1 / 修复 1 / 清空");
        command.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        command.setFont(mono);
        command.addSelectionListener(new SelectionAdapter() { @Override public void widgetDefaultSelected(SelectionEvent e) {
            String value = command.getText().trim(); command.setText(""); dispatch(value);
        }});
        showHelp();
        applyTheme(ThemeController.isDark());
    }
    private Composite frame(Composite parent, boolean grow) {
        Composite frame = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 1; layout.marginHeight = 1;
        frame.setLayout(layout);
        frame.setLayoutData(new GridData(SWT.FILL, grow ? SWT.FILL : SWT.CENTER, true, grow));
        return frame;
    }
    public void showHelp() {
        output.setText("[ 就绪 ] 离线排错已加载\n\n关闭后重新打开：\n顶栏【ZenCode 工具 → 打开离线排错】。\n也可通过【窗口 → 显示视图 → 其他 → ZenCode → 离线排错】打开。\n关闭面板不会停用 Java 快速修复。\n\n切换外观：\n顶栏【ZenCode 工具 → 深色模式 / 浅色模式】。\n设置会保存，下次启动自动恢复。Windows 原生菜单栏需要重启才能完整更新。\n\n> 帮助 / help\n显示本说明。\n\n> 检查 / check\n检查当前 Java 文件的编译诊断、部分结构风险及全角标点。\n\n> 预览 1 / preview 1\n查看第 1 条建议。\n\n> 修复 1 / fix 1\n确认后应用，可在编辑器中撤销。\n\n> 清空 / clear\n清空输出。\n\n断点：把光标放在可执行语句上，按 Ctrl+Shift+B，或使用顶栏【ZenCode 工具 → 切换当前行断点】。左侧圆点表示已设置断点。\n\n中文显示方框但复制后正常：使用【ZenCode 工具 → 恢复 Java 默认字体（中文显示）】。若复制出来也已是问号，再检查文件编码并从原文恢复；不要对现有 GBK 文件直接强制转换。\n\n方法名拼写错误：在 Java 编辑器中使用 Ctrl+1，查看带 [ZenCode] 的建议。\n完整编译诊断请查看底部【问题】面板。\n");
    }
    public void applyTheme(boolean dark) {
        if (output == null || output.isDisposed()) return;
        Color[] old = {background, foreground, accent, warning, error};
        org.eclipse.swt.widgets.Display display = output.getDisplay();
        background = dark ? new Color(display, 30, 34, 39) : new Color(display, 250, 252, 250);
        foreground = dark ? new Color(display, 220, 230, 222) : new Color(display, 28, 40, 32);
        accent = dark ? new Color(display, 126, 231, 166) : new Color(display, 20, 105, 55);
        warning = dark ? new Color(display, 230, 199, 136) : new Color(display, 130, 75, 0);
        error = dark ? new Color(display, 255, 150, 150) : new Color(display, 175, 30, 30);
        for (org.eclipse.swt.widgets.Control control : new org.eclipse.swt.widgets.Control[] {container, title, check, output, command}) {
            control.setBackground(background); control.setForeground(foreground);
        }
        outputFrame.setBackground(accent);
        commandFrame.setBackground(accent);
        title.setForeground(accent);
        output.setStyleRanges(new StyleRange[0]);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[ (就绪|警告|错误|提示|预览|检查)[^\\]]*\\]").matcher(output.getText());
        while (matcher.find()) {
            Color color = "错误".equals(matcher.group(1)) ? error : "警告".equals(matcher.group(1)) ? warning : accent;
            output.setStyleRange(new StyleRange(matcher.start(), matcher.end() - matcher.start(), color, background));
        }
        for (Color color : old) if (color != null && !color.isDisposed()) color.dispose();
    }
    private void dispatch(String value) {
        if ("help".equals(value) || "帮助".equals(value)) { showHelp(); return; }
        if ("check".equals(value) || "检查".equals(value)) { checkEditor(); return; }
        if ("clear".equals(value) || "清空".equals(value)) { output.setText(""); return; }
        value = value.replaceFirst("^预览 ", "preview ").replaceFirst("^修复 ", "fix ");
        if (value.matches("(preview|fix) [1-9][0-9]{0,3}")) {
            int index = Integer.parseInt(value.substring(value.indexOf(' ') + 1)) - 1;
            if (index >= diagnostics.size() || checkedDocument == null) { append("[ 警告 ] 没有该条诊断，请先执行检查。"); return; }
            TextFix fix = diagnostics.get(index).getFix();
            if (!fix.isCurrent(checkedDocument.get())) { append("[ 警告 ] 文件已变化，请重新检查。"); return; }
            if (getCurrentDocument() != checkedDocument) { append("[ 警告 ] 当前编辑器已经改变，请返回原文件或重新检查。"); return; }
            String before = checkedDocument.get().substring(fix.getOffset(), fix.getOffset() + fix.getLength());
            String preview = "第 " + diagnostics.get(index).getLine() + " 行：\n「" + before + "」 → 「" + fix.getReplacement() + "」";
            append("[ 预览 ] " + preview);
            if (value.startsWith("fix ") && MessageDialog.openConfirm(getSite().getShell(), "ZenCode · 应用修改", preview + "\n\n应用后可在编辑器中撤销。")) {
                if (!fix.isCurrent(checkedDocument.get())) { append("[ 警告 ] 文件已变化，请重新检查。"); return; }
                try { checkedDocument.replace(fix.getOffset(), fix.getLength(), fix.getReplacement()); checkEditor(); }
                catch (BadLocationException ex) { append("[ 错误 ] 编辑位置失效，请重新检查。"); }
            }
            return;
        }
        append("[ 警告 ] 未知命令。输入帮助或 help 查看可用操作。");
    }
    private IDocument getCurrentDocument() {
        IEditorPart part = getSite().getPage().getActiveEditor();
        if (!(part instanceof ITextEditor) || !part.getEditorInput().getName().endsWith(".java")) return null;
        ITextEditor editor = (ITextEditor) part;
        return editor.getDocumentProvider().getDocument(editor.getEditorInput());
    }
    private void checkEditor() {
        diagnostics.clear(); checkedDocument = getCurrentDocument();
        if (checkedDocument == null) { append("[ 提示 ] 请先打开一个 .java 文件。此检查器不解析 JSP/JS。"); return; }
        String source = checkedDocument.get();
        if (source.length() > 200000) { append("[ 警告 ] 此原型仅检查 200,000 字符以内的文件；大文件请使用 JDT 诊断。"); return; }
        if (source.contains("\\u")) { append("[ 提示 ] 文件包含 Unicode 转义，已交由 JDT 处理，避免标点误判。"); return; }
        diagnostics.addAll(new JavaPunctuationCheck().check(source));
        output.setText("[ 检查 ] Java 标点检查：" + diagnostics.size() + " 条建议\n以下编号仅用于标点修复；编译诊断和风险检查列在后面。\n");
        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic d = diagnostics.get(i);
            append("[ 警告 " + (i + 1) + " ] 第 " + d.getLine() + " 行：" + d.getMessage());
        }
        org.eclipse.jdt.core.IJavaProject project = null;
        org.eclipse.core.resources.IFile file = getSite().getPage().getActiveEditor().getEditorInput().getAdapter(org.eclipse.core.resources.IFile.class);
        if (file != null) {
            project = org.eclipse.jdt.core.JavaCore.create(file.getProject());
            try {
                for (org.eclipse.core.resources.IMarker marker : file.getProject().findMarkers("org.eclipse.jdt.core.problem", true, org.eclipse.core.resources.IResource.DEPTH_ZERO))
                    append("[ 错误 ] 项目配置：" + marker.getAttribute(org.eclipse.core.resources.IMarker.MESSAGE, "请检查构建路径") + "\n请在项目属性 → Java 构建路径中绑定 JDK 8 的 JRE 系统库。");
            } catch (org.eclipse.core.runtime.CoreException e) { append("[ 警告 ] 无法读取项目诊断：" + e.getMessage()); }
        }
        try {
            for (String message : JavaReview.check(source, getSite().getPage().getActiveEditor().getEditorInput().getName(), project)) append(message);
        } catch (RuntimeException e) { append("[ 错误 ] 代码分析失败：" + e.getMessage()); }
    }
    private void append(String text) {
        int start = output.getCharCount() + 1;
        output.append("\n" + text + "\n");
        int end = text.indexOf(']');
        if (end >= 0) {
            Color color = text.startsWith("[ 错误") ? error : text.startsWith("[ 警告") ? warning : accent;
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
