package cn.zencode.ide.webchecks;

import cn.zencode.assist.core.webchecks.WebChecks;
import org.eclipse.core.commands.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.*;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/** Explicit read-only check of the current unsaved editor snapshot. */
public final class WebCheckCommand extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        IEditorPart part = window.getActivePage().getActiveEditor();
        ITextEditor editor = part instanceof ITextEditor ? (ITextEditor) part : part == null ? null : part.getAdapter(ITextEditor.class);
        if (editor == null) { MessageDialog.openInformation(window.getShell(), "Web 检查", "请先打开文本文件。"); return null; }
        IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
        if (doc == null) return null;
        String name = editor.getEditorInput().getName();
        String extension = name.lastIndexOf('.') < 0 ? "" : name.substring(name.lastIndexOf('.') + 1);
        final String snapshot = doc.get();
        final boolean compareParameterHints = MessageDialog.openQuestion(window.getShell(), "表单参数关联建议",
            "是否将当前文件中明确 form action 指向的本地 JSP、或 web.xml 精确路由指向的 Servlet 源文件作为候选配对，比较控件 name 与目标源码中 getParameter 的字符串参数？\n\n这是人工选择的文本关联：无法证明运行关系、接收者类型或参数来源；仅提供建议，不自动修改。选择否仍执行资源和已有诊断检查。");
        final org.eclipse.core.resources.IFile file = editor.getEditorInput().getAdapter(org.eclipse.core.resources.IFile.class);
        org.eclipse.core.runtime.jobs.Job job = new org.eclipse.core.runtime.jobs.Job("离线 Web 检查") {
            @Override protected org.eclipse.core.runtime.IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                final java.util.List<cn.zencode.assist.core.webchecks.ProjectWebChecks.Finding> locations = new java.util.ArrayList<>();
                final StringBuilder text = new StringBuilder("当前快照只读检查；定位格式：文件:字符偏移。\n\n");
                try {
                    for (String issue : WebChecks.check(extension, snapshot)) text.append(issue).append('\n');
                    if (file != null) {
                        final java.util.Map<String,String> files = new java.util.LinkedHashMap<String,String>();
                        final int[] total = {0};
                        file.getProject().accept(resource -> {
                            if (resource.isLinked()) return false;
                            if (monitor.isCanceled() || files.size() >= 200 || total[0] >= 4000000) return false;
                            if (resource instanceof org.eclipse.core.resources.IFile) {
                                org.eclipse.core.resources.IFile candidate = (org.eclipse.core.resources.IFile)resource;
                                String path = candidate.getProjectRelativePath().toPortableString();
                                if (candidate.isLinked()) return false;
                                // Assets need existence only; text snapshots have explicit byte and aggregate bounds.
                                String contents = "";
                                if (path.matches("(?i).*\\.(jsp|html?|css|js|java|xml)")) {
                                    try (java.io.InputStream in = candidate.getContents()) {
                                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                                        byte[] buffer = new byte[8192]; int n;
                                        while ((n=in.read(buffer))!=-1 && bytes.size()+n<=1000000) bytes.write(buffer,0,n);
                                        if(n!=-1) return false;
                                        contents = new String(bytes.toByteArray(), candidate.getCharset());
                                        total[0]+=contents.length();
                                    } catch (java.io.IOException e) { return false; }
                                }
                                files.put(path,contents);
                            }
                            return true;
                        });
                        String current = file.getProjectRelativePath().toPortableString();
                        files.put(current,snapshot);
                        text.append("资源检查范围：最多200文件/400万字符；根路径、动态表达式及查询串跳过。\n");
                        for (cn.zencode.assist.core.webchecks.ProjectWebChecks.Finding f : cn.zencode.assist.core.webchecks.ProjectWebChecks.check(current,files,compareParameterHints ? parameterHints(files,org.eclipse.jdt.core.JavaCore.create(file.getProject())) : java.util.Collections.emptyMap())) { if(locations.size()<100) locations.add(f); if(text.length()<28000) text.append(f).append('\n'); }
                        text.append("已保存内容的 Eclipse/WTP 诊断（未保存内容可能不同）：\n");
                        for (org.eclipse.core.resources.IMarker marker : file.findMarkers(org.eclipse.core.resources.IMarker.PROBLEM,true,org.eclipse.core.resources.IResource.DEPTH_ZERO))
                            text.append("行 ").append(marker.getAttribute(org.eclipse.core.resources.IMarker.LINE_NUMBER,-1)).append("：").append(marker.getAttribute(org.eclipse.core.resources.IMarker.MESSAGE,"")).append('\n');
                    }
                } catch (org.eclipse.core.runtime.CoreException e) { text.append("检查不完整：").append(e.getStatus().getMessage()); }
                final String report = text.length()>30000 ? text.substring(0,30000)+"\n报告已截断" : text.toString();
                window.getShell().getDisplay().asyncExec(() -> {
                    if (!window.getShell().isDisposed()) {
                        if(locations.isEmpty()) MessageDialog.openInformation(window.getShell(), "离线 Web 检查", report);
                        else {
                            org.eclipse.ui.dialogs.ListDialog dialog = new org.eclipse.ui.dialogs.ListDialog(window.getShell());
                            dialog.setTitle("离线 Web 检查 — 选择建议定位");
                            dialog.setMessage(report.length()>6000 ? report.substring(0,6000)+"\n报告已截断" : report);
                            dialog.setContentProvider(org.eclipse.jface.viewers.ArrayContentProvider.getInstance());
                            dialog.setLabelProvider(new org.eclipse.jface.viewers.LabelProvider());
                            dialog.setInput(locations);
                            if(dialog.open()==org.eclipse.jface.window.Window.OK && dialog.getResult()!=null && dialog.getResult().length>0) {
                                cn.zencode.assist.core.webchecks.ProjectWebChecks.Finding selected=(cn.zencode.assist.core.webchecks.ProjectWebChecks.Finding)dialog.getResult()[0];
                                // Offsets refer to the captured editor snapshot; refuse stale locations.
                                if(!snapshot.equals(doc.get())) { MessageDialog.openInformation(window.getShell(),"定位已过期","源码已变化，请重新检查。"); return; }
                                editor.selectAndReveal(selected.offset,0);
                                window.getActivePage().activate(editor);
                            }
                        }
                    }
                });
                return org.eclipse.core.runtime.Status.OK_STATUS;
            }
        };
        job.schedule();
        return null;
    }
    /** AST syntax only: deliberately no claim of ServletRequest binding. Explicit opt-in at invocation. */
    private static java.util.Map<String,java.util.Set<String>> parameterHints(java.util.Map<String,String> files, org.eclipse.jdt.core.IJavaProject project) {
        java.util.Map<String,java.util.Set<String>> result = new java.util.HashMap<>();
        for(java.util.Map.Entry<String,String> entry:files.entrySet()) {
            if(!entry.getKey().endsWith(".jsp") && !entry.getKey().endsWith(".java")) continue;
            String jsp=entry.getValue();
            if(entry.getKey().endsWith(".jsp")) {
                java.util.Set<String> bound=JspJavaBindings.requestParameters(jsp,project);
                if(!bound.isEmpty()) {result.put(entry.getKey(),bound);continue;}
            }
            StringBuilder scriptlet=new StringBuilder();
            java.util.regex.Matcher fragments=java.util.regex.Pattern.compile("(?s)<%(?![-@!=])(.*?)%>").matcher(jsp.replaceAll("(?s)<%--.*?--%>", ""));
            while(fragments.find()) scriptlet.append(fragments.group(1)).append('\n');
            org.eclipse.jdt.core.dom.ASTParser parser=org.eclipse.jdt.core.dom.ASTParser.newParser(org.eclipse.jdt.core.dom.AST.JLS8);
            parser.setKind(entry.getKey().endsWith(".java") ? org.eclipse.jdt.core.dom.ASTParser.K_COMPILATION_UNIT : org.eclipse.jdt.core.dom.ASTParser.K_STATEMENTS);
            parser.setSource((entry.getKey().endsWith(".java") ? jsp : scriptlet.toString()).toCharArray());
            final java.util.Set<String> parameters=new java.util.TreeSet<>();
            parser.createAST(null).accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
                @Override public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
                    if(node.getExpression()!=null && "getParameter".equals(node.getName().getIdentifier()) && node.arguments().size()==1 && node.arguments().get(0) instanceof org.eclipse.jdt.core.dom.StringLiteral)
                        parameters.add(((org.eclipse.jdt.core.dom.StringLiteral)node.arguments().get(0)).getLiteralValue());
                    return true;
                }
            });
            result.put(entry.getKey(),parameters);
        }
        return result;
    }
}
