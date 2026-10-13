package cn.zencode.ide;

import java.util.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;

/** Conservative structural review; suggestions never rewrite source. */
public final class JavaReview {
    public static List<String> check(String source, String name, IJavaProject project) {
        final List<String> result = new ArrayList<String>();
        ASTParser parser = ASTParser.newParser(AST.JLS14);
        if (project != null) { parser.setProject(project); parser.setResolveBindings(true); }
        else { Map<String, String> options = JavaCore.getOptions(); JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options); parser.setCompilerOptions(options); }
        parser.setSource(source.toCharArray()); parser.setUnitName(name);
        final CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        if ("module-info.java".equals(name)) result.add("[ 警告 ] 当前文件是模块描述文件。普通类请保存为同名 .java 文件；JDK 8 不支持模块描述文件。");
        for (IProblem p : unit.getProblems()) if (p.isError() || p.isWarning())
            result.add("[ " + (p.isError() ? "错误" : "警告") + " ] 第 " + p.getSourceLineNumber() + " 行：" + p.getMessage());
        unit.accept(new ASTVisitor() {
            void warn(ASTNode n, String message) { result.add("[ 警告 ] 第 " + unit.getLineNumber(n.getStartPosition()) + " 行：" + message); }
            public boolean visit(CatchClause n) {
                if (n.getBody().statements().isEmpty()) warn(n, "空 catch 会吞掉异常，请记录或处理失败。"); return true;
            }
            public boolean visit(ReturnStatement n) {
                for (ASTNode a=n.getParent(); a!=null && !(a instanceof MethodDeclaration); a=a.getParent())
                    if (a.getParent() instanceof TryStatement && ((TryStatement)a.getParent()).getFinally()==a) { warn(n,"finally 中的 return 会覆盖原返回值或异常。"); break; }
                return true;
            }
            public boolean visit(InfixExpression n) {
                if ((n.getOperator()==InfixExpression.Operator.EQUALS || n.getOperator()==InfixExpression.Operator.NOT_EQUALS)
                    && (n.getLeftOperand() instanceof StringLiteral || n.getRightOperand() instanceof StringLiteral || isString(n.getLeftOperand()) || isString(n.getRightOperand())))
                    warn(n,"字符串内容比较请使用 equals；== 比较对象引用。");
                if(n.getOperator()==InfixExpression.Operator.LESS_EQUALS && n.getRightOperand() instanceof QualifiedName && ((QualifiedName)n.getRightOperand()).getName().getIdentifier().equals("length"))
                    warn(n,"边界可能越界：数组有效下标小于 length，请核对循环条件和访问下标。");
                if(n.getOperator()==InfixExpression.Operator.PLUS && n.getLeftOperand() instanceof StringLiteral && ((StringLiteral)n.getLeftOperand()).getLiteralValue().toUpperCase(Locale.ROOT).contains("SELECT "))
                    warn(n,"SQL 字符串拼接：若拼入外部输入并执行，存在注入风险，请使用预编译参数。");
                return true;
            }
            boolean isString(Expression e) { ITypeBinding b=e.resolveTypeBinding(); return b!=null && "java.lang.String".equals(b.getQualifiedName()); }
            public boolean visit(FieldDeclaration n) {
                if(Modifier.isPublic(n.getModifiers()) && Modifier.isStatic(n.getModifiers()) && !Modifier.isFinal(n.getModifiers())) warn(n,"公开的可变静态字段会暴露全局状态，请检查封装。");
                for(Object o:n.fragments()) { VariableDeclarationFragment f=(VariableDeclarationFragment)o;
                    if(f.getName().getIdentifier().toLowerCase(Locale.ROOT).contains("password") && f.getInitializer() instanceof StringLiteral) warn(f,"疑似硬编码密码，请从外部配置读取，避免提交凭据。"); }
                return true;
            }
        });
        result.add("[ 提示 ] 检查覆盖编译诊断及部分结构风险；业务规则、金额校验、返回值约定等仍需人工确认。风险建议不提供自动修改。");
        return result;
    }
}
