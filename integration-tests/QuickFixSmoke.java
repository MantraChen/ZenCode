import cn.zencode.ide.TypoQuickFixProcessor;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jface.text.Document;

/** Headless smoke tests against the exact JDT and plugin jars shipped in the product. */
public final class QuickFixSmoke {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String review = cn.zencode.ide.JavaReview.check("class X { boolean f(String s) { return s == \"x\"; } int g() { try {return 1;} finally {return 0;} } void h() {try {} catch(Exception e) {}} }", "X.java", null).toString();
        if (!review.contains("字符串内容比较") || !review.contains("finally") || !review.contains("空 catch")) throw new AssertionError(review);
        String clean = cn.zencode.ide.JavaReview.check("class X { boolean f(String s) {return \"x\".equals(s);} }", "X.java", null).toString();
        if (clean.contains("字符串内容比较") || clean.contains("空 catch")) throw new AssertionError(clean);
        System.out.println("PASS structural review findings and clean-code counterexample.");
        check("public int get(int x) { return x; }", "new Bean().gte(1)", "get", "");
        check("private int get(int x) { return x; }", "new Bean().gte(1)", null, "");
        check("public int get(int x) { return x; }", "new Bean().gte(true)", null, "");
        check("public int get(int x) { return x; }", "Bean.gte(1)", null, "");
        check("public static int get(int x) { return x; }", "Bean.gte(1)", "get", "");
        check("", "new Bean().gte(1)", "get", "class Parent { public int get(int x) { return x; } }");
        check("public int get(int... x) { return 0; }", "new Bean().gte(1)", null, "");
        check("public <T> int get(T x) { return 0; }", "new Bean().gte(1)", null, "");
        System.out.println("PASS " + checks + " real-JDT quick-fix scenarios (no desktop UI started).");
    }
    private static void check(String methods, String expression, String expected, String parent) throws Exception {
        final String source = parent + " class Bean" + (parent.isEmpty() ? "" : " extends Parent")
                + " { " + methods + " } class Example { void run() { " + expression + "; } }";
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(source.toCharArray()); parser.setUnitName("Example.java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true); parser.setBindingsRecovery(true);
        Map<String, String> options = new HashMap<String, String>();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options); parser.setCompilerOptions(options);
        final CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        final MethodInvocation[] invocation = { null };
        ast.accept(new ASTVisitor() { @Override public boolean visit(MethodInvocation node) {
            if ("gte".equals(node.getName().getIdentifier())) invocation[0] = node;
            return true;
        }});
        if (invocation[0] == null || invocation[0].getExpression().resolveTypeBinding() == null) throw new AssertionError("Missing real binding");
        boolean undefined = false;
        for (IProblem problem : ast.getProblems()) if (problem.getID() == IProblem.UndefinedMethod) undefined = true;
        if (!undefined) throw new AssertionError("Expected genuine undefined-method compiler diagnostic");
        IBuffer buffer = proxy(IBuffer.class, (object, method, arguments) -> "getContents".equals(method.getName()) ? source : null);
        ICompilationUnit unit = proxy(ICompilationUnit.class, (object, method, arguments) -> "getBuffer".equals(method.getName()) ? buffer : null);
        IInvocationContext context = proxy(IInvocationContext.class, (object, method, arguments) -> {
            if ("getCompilationUnit".equals(method.getName())) return unit;
            if ("getASTRoot".equals(method.getName())) return ast;
            return null;
        });
        IProblemLocation location = proxy(IProblemLocation.class, (object, method, arguments) -> {
            if ("getProblemId".equals(method.getName())) return IProblem.UndefinedMethod;
            if ("getCoveringNode".equals(method.getName())) return invocation[0].getName();
            return null;
        });
        IJavaCompletionProposal[] proposals = new TypoQuickFixProcessor().getCorrections(context, new IProblemLocation[] {location});
        if (expected == null) {
            if (proposals.length != 0) throw new AssertionError("Unsafe candidate for " + expression);
        } else {
            if (proposals.length != 1 || !proposals[0].getDisplayString().contains("「" + expected + "」")) throw new AssertionError("Missing candidate for " + expression);
            Document document = new Document(source);
            proposals[0].apply(document);
            if (!document.get().equals(source.replace(".gte(", "." + expected + "("))) throw new AssertionError("Incorrect edit");
        }
        checks++;
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
