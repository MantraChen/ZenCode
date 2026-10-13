package cn.zencode.ide;

import cn.zencode.assist.core.NameSuggestions;
import cn.zencode.assist.core.TextFix;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickFixProcessor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

/** Conservative first integration: explicit receivers and public, fixed-arity methods. */
public final class TypoQuickFixProcessor implements IQuickFixProcessor {
    @Override public boolean hasCorrections(ICompilationUnit unit, int problemId) {
        return problemId == IProblem.UndefinedMethod;
    }

    @Override public IJavaCompletionProposal[] getCorrections(IInvocationContext context,
            IProblemLocation[] locations) throws CoreException {
        List<IJavaCompletionProposal> proposals = new ArrayList<IJavaCompletionProposal>();
        String snapshot = context.getCompilationUnit().getBuffer().getContents();
        Set<String> emitted = new HashSet<String>();
        for (IProblemLocation problem : locations) {
            if (problem.getProblemId() != IProblem.UndefinedMethod) continue;
            ASTNode node = problem.getCoveringNode(context.getASTRoot());
            while (node != null && !(node instanceof MethodInvocation)) node = node.getParent();
            if (!(node instanceof MethodInvocation)) continue;
            MethodInvocation call = (MethodInvocation) node;
            Expression receiver = call.getExpression();
            if (receiver == null) continue; // Implicit this/super is delegated to existing JDT fixes.
            ITypeBinding type = receiver.resolveTypeBinding();
            if (type == null || type.isRecovered()) continue;
            boolean staticOnly = false;
            if (receiver instanceof Name) {
                IBinding binding = ((Name) receiver).resolveBinding();
                staticOnly = binding instanceof ITypeBinding;
            }
            List<String> eligible = new ArrayList<String>();
            collect(type, call, staticOnly, true, eligible, new HashSet<String>());
            for (String name : NameSuggestions.suggest(call.getName().getIdentifier(), eligible)) {
                int offset = call.getName().getStartPosition();
                String key = offset + ":" + name;
                if (emitted.add(key)) proposals.add(new Proposal(
                        new TextFix(snapshot, offset, call.getName().getLength(), name),
                        call.getName().getIdentifier(), name));
            }
        }
        return proposals.toArray(new IJavaCompletionProposal[proposals.size()]);
    }

    private void collect(ITypeBinding type, MethodInvocation call, boolean staticOnly, boolean root,
            List<String> names, Set<String> visited) {
        if (type == null || type.isRecovered() || !visited.add(type.getKey())) return;
        for (IMethodBinding method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isConstructor() || method.isVarargs()
                    || method.isRecovered() || method.getTypeParameters().length > 0) continue;
            if (staticOnly && !Modifier.isStatic(method.getModifiers())) continue;
            // Static interface methods are not inherited by subinterfaces or implementing classes.
            if (!root && type.isInterface() && Modifier.isStatic(method.getModifiers())) continue;
            ITypeBinding[] parameters = method.getParameterTypes();
            if (parameters.length != call.arguments().size()) continue;
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                ITypeBinding argument = ((Expression) call.arguments().get(i)).resolveTypeBinding();
                if (argument == null || argument.isRecovered() || !argument.isAssignmentCompatible(parameters[i])) {
                    compatible = false; break;
                }
            }
            if (compatible) names.add(method.getName());
        }
        collect(type.getSuperclass(), call, staticOnly, false, names, visited);
        for (ITypeBinding parent : type.getInterfaces()) collect(parent, call, staticOnly, false, names, visited);
    }

    private static final class Proposal implements IJavaCompletionProposal {
        private final TextFix fix;
        private final String previous, candidate;
        Proposal(TextFix fix, String previous, String candidate) {
            this.fix = fix; this.previous = previous; this.candidate = candidate;
        }
        @Override public void apply(IDocument document) {
            if (!fix.isCurrent(document.get())) { inform("源码已经变化，请重新打开快速修复菜单。"); return; }
            try { document.replace(fix.getOffset(), fix.getLength(), fix.getReplacement()); }
            catch (BadLocationException ex) { inform("修改位置已经失效，请重新检查。"); }
        }
        private void inform(String message) {
            Display display = Display.getCurrent();
            if (display != null && display.getActiveShell() != null) MessageDialog.openInformation(display.getActiveShell(), "ZenCode", message);
        }
        @Override public Point getSelection(IDocument document) { return new Point(Math.min(document.getLength(), fix.getOffset() + candidate.length()), 0); }
        @Override public String getAdditionalProposalInfo() {
            return "<b>ZenCode 离线拼写建议</b><br>" + previous + " → " + candidate
                    + "<br>候选来自接收者类型的公开方法，并通过参数类型检查。<br>这仍是建议；应用后请检查编译结果。";
        }
        @Override public String getDisplayString() { return "[ZenCode] 将「" + previous + "」改为「" + candidate + "」"; }
        @Override public Image getImage() { return null; }
        @Override public IContextInformation getContextInformation() { return null; }
        @Override public int getRelevance() { return 50 - NameSuggestions.distance(previous, candidate); }
    }
}
