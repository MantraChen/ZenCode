package cn.zencode.ide.webchecks;

import java.util.*;
import java.util.regex.*;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.*;

/** Bounded synthetic compilation unit; only resolved javax.servlet request calls are exported. */
public final class JspJavaBindings {
    private JspJavaBindings() { }
    public static Set<String> requestParameters(String jsp,IJavaProject project) {
        if(project==null || !project.exists() || jsp.length()>1000000)return Collections.emptySet();
        // Includes/declarations/custom tags can alter the translation context: decline rather than guess.
        if(jsp.contains("<%@ include") || jsp.contains("<%!") || jsp.matches("(?s).*<[A-Za-z]+:[A-Za-z]+.*"))return Collections.emptySet();
        String clean=jsp.replaceAll("(?s)<%--.*?--%>","");
        StringBuilder imports=new StringBuilder();
        Matcher directives=Pattern.compile("(?s)<%@\\s*page\\b(.*?)%>").matcher(clean);
        while(directives.find()) {
            Matcher names=Pattern.compile("\\bimport\\s*=\\s*[\"']([^\"']+)[\"']").matcher(directives.group(1));
            while(names.find())for(String name:names.group(1).split(","))if(name.trim().matches("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*(?:\\.\\*)?"))imports.append("import ").append(name.trim()).append(";\n");
        }
        StringBuilder body=new StringBuilder();
        Matcher fragments=Pattern.compile("(?s)<%(?![-@!])(.*?)%>").matcher(clean);
        while(fragments.find()) {
            String fragment=fragments.group(1);
            if(fragment.startsWith("="))body.append("out.print(").append(fragment.substring(1)).append(");\n");
            else body.append(fragment).append('\n');
        }
        String source=imports+"class __ZenCodeJsp { void service(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, javax.servlet.http.HttpSession session, javax.servlet.ServletContext application, javax.servlet.ServletConfig config, javax.servlet.jsp.JspWriter out, javax.servlet.jsp.PageContext pageContext) throws java.lang.Exception { Object page=this; "+body+" }}";
        ASTParser parser=ASTParser.newParser(AST.JLS8);
        parser.setProject(project); parser.setUnitName("__ZenCodeJsp.java"); parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        final Set<String> result=new TreeSet<String>();
        parser.createAST(null).accept(new ASTVisitor(){
            @Override public boolean visit(MethodInvocation node) {
                IMethodBinding binding=node.resolveMethodBinding();
                if(binding==null || binding.isRecovered() || !"getParameter".equals(binding.getName()) || node.arguments().size()!=1 || !(node.arguments().get(0) instanceof StringLiteral))return true;
                ITypeBinding owner=binding.getDeclaringClass();
                if(owner!=null&&!owner.isRecovered()&&("javax.servlet.ServletRequest".equals(owner.getQualifiedName())||"javax.servlet.http.HttpServletRequest".equals(owner.getQualifiedName())))
                    result.add(((StringLiteral)node.arguments().get(0)).getLiteralValue());
                return true;
            }
        });
        return result;
    }
}
