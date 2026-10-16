package cn.zencode.assist.core.database;
import java.sql.SQLException;
/** Safe diagnostics deliberately discard driver messages, URLs and supplied values. */
public final class DriverDiagnostics {
    public static String describe(Throwable failure) {
        if (failure instanceof ClassNotFoundException) return "找不到驱动类，请核对类全名并同时选择全部本地依赖 JAR。";
        if (failure instanceof LinkageError) return "驱动类无法链接：可能缺少依赖或与 Java 8 不兼容，请检查所选 JAR。";
        if (failure instanceof java.io.IOException) return "驱动 JAR 无效或不兼容 Java 8，请检查本地文件。";
        if (failure instanceof SQLException) {
            String state = ((SQLException)failure).getSQLState();
            if (state != null && state.startsWith("28")) return "数据库身份验证失败，请检查用户名、密码和访问权限。";
            if (state != null && state.startsWith("08")) return "无法建立数据库连接，请检查服务状态、地址、端口和网络。";
            if (state != null && state.startsWith("0A")) return "驱动不支持所需的元数据操作，请核对驱动版本与数据库兼容性。";
        }
        return "连接或元数据读取失败：请核对驱动依赖、服务与访问权限。为保护凭据，不显示驱动原始错误。";
    }
    private DriverDiagnostics() { }
}
