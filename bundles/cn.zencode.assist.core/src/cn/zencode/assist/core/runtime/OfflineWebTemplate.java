package cn.zencode.assist.core.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Offline source sample export. The destination must be new; no existing project is modified. */
public final class OfflineWebTemplate {
    private OfflineWebTemplate() { }
    public static Map<String, String> files() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("WebContent/index.jsp", "<%@ page contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\" %>\n<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>ZenCode Web</title></head><body><h1>READY / JSP</h1><p><%= java.time.LocalDate.now() %></p><a href=\"hello\">Servlet 测试</a></body></html>\n");
        files.put("src/example/HelloServlet.java", "package example;\nimport java.io.IOException;\nimport javax.servlet.ServletException;\nimport javax.servlet.http.*;\n@javax.servlet.annotation.WebServlet(\"/hello\")\npublic class HelloServlet extends HttpServlet {\n protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {\n  response.setContentType(\"text/plain; charset=UTF-8\");\n  response.getWriter().println(\"READY / Servlet\");\n }\n}\n");
        files.put("WebContent/WEB-INF/web.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<web-app xmlns=\"http://java.sun.com/xml/ns/javaee\" version=\"3.0\"><welcome-file-list><welcome-file>index.jsp</welcome-file></welcome-file-list></web-app>\n");
        files.put("src/example/JdbcSample.java", "package example;\nimport java.sql.*;\npublic final class JdbcSample {\n private JdbcSample() {}\n // Explicit caller supplies credentials; no connection occurs automatically.\n public static String lookup(String url, String user, String password, long id) throws SQLException {\n  try (Connection c = DriverManager.getConnection(url, user, password);\n       PreparedStatement s = c.prepareStatement(\"SELECT name FROM example_items WHERE id = ?\")) {\n   s.setLong(1, id); s.setQueryTimeout(10); s.setMaxRows(1);\n   try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }\n  }\n }\n}\n");
        files.put("README.txt", "离线 JSP / Servlet / JDBC 示例（Java 8，javax.servlet，Web facet 3.0）\n通过新建 Web 示例工程入口创建时，文件已按工程实际目录自动加入。选择本地 Tomcat 7.0/8.0/8.5/9.0 与 JDK 8。\n仅使用目录导出功能时：先创建空 Dynamic Web Project，再将 src 和 WebContent 内容复制到对应目录；检查冲突，不覆盖已有源文件。\n项目属性 → Targeted Runtimes 绑定对应服务器，保持 Dynamic Web Module 3.0。右键工程 → Run As → Run on Server；访问 index.jsp 和 hello。\nJava 断点用 Debug on Server；JSP 断点和重新发布行为需按实际服务器验证。停止操作使用 Servers 视图。\nJDBC 示例仅在显式调用时连接。自行提供兼容 Java 8 且许可适当的驱动到 WEB-INF/lib；不自动下载驱动、不创建数据库、不执行建表或修改 SQL。example_items 表需由使用者准备。密码不要写入源码。\nTomcat 7/8 为旧工程兼容；此模板不代表服务器启动或 Windows 实机已通过验证。\n");
        return java.util.Collections.unmodifiableMap(files);
    }
    public static void create(Path destination) throws IOException {
        Map<String, String> files = files();
        Files.createDirectory(destination);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = destination.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue().getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE_NEW);
        }
    }
}
