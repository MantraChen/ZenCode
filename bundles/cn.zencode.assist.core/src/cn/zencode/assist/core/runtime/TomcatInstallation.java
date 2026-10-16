package cn.zencode.assist.core.runtime;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.jar.JarFile;

public final class TomcatInstallation {
    private TomcatInstallation() {}
    public static String inspect(File home) throws java.io.IOException {
        for (String path : new String[] {"conf/server.xml", "bin/bootstrap.jar", "lib/servlet-api.jar", "lib/catalina.jar"}) {
            if (!new File(home, path).isFile()) throw new java.io.IOException("缺少 " + path + "；请选择完整安装目录。");
        }
        Properties properties = new Properties();
        try (JarFile jar = new JarFile(new File(home, "lib/catalina.jar"))) {
            java.util.jar.JarEntry entry = jar.getJarEntry("org/apache/catalina/util/ServerInfo.properties");
            if (entry == null) throw new java.io.IOException("缺少 Tomcat 版本信息");
            try (InputStream input = jar.getInputStream(entry)) { properties.load(input); }
        }
        String version = properties.getProperty("server.number", "");
        if (!version.matches("(?:7\\.0|8\\.0|8\\.5|9\\.0)\\.[0-9]+(?:\\.[0-9]+)?")) {
            throw new java.io.IOException("不在当前适配范围的版本：" + version + "。支持 7.0 / 8.0 / 8.5 / 9.0；旧版本仅用于旧工程兼容。");
        }
        return "READY / 已识别 Tomcat " + version + "\n" + home.getAbsolutePath() + "\n安装文件与版本信息检查通过。推荐使用 Tomcat 9.0 与 Java 8 的兼容组合。";
    }
}
