package cn.zencode.assist.core.database;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Offline inspection; does not load or execute driver code. */
public final class DriverCompatibility {
    private DriverCompatibility() { }
    public static void requireJava8(String path) throws IOException {
        boolean classes = false;
        try (JarFile jar = new JarFile(path)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/versions/")) continue;
                classes = true;
                try (InputStream in = jar.getInputStream(entry); DataInputStream data = new DataInputStream(in)) {
                    if (data.readInt() != 0xCAFEBABE) throw new IOException("驱动包含无效的 class 文件。");
                    data.readUnsignedShort();
                    if (data.readUnsignedShort() > 52) throw new IOException("驱动要求高于 Java 8 的运行环境，请选择 Java 8 兼容驱动。");
                }
            }
        }
        if (!classes) throw new IOException("所选 JAR 中没有 Java 类。");
    }
}
