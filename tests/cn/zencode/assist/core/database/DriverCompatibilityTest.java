package cn.zencode.assist.core.database;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
public class DriverCompatibilityTest {
    @Test public void rejectsNewerBytecodeBeforeLoading() throws Exception {
        Path file = Files.createTempFile("driver-check", ".jar");
        try {
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
                out.putNextEntry(new JarEntry("Example.class"));
                DataOutputStream data = new DataOutputStream(out);
                data.writeInt(0xCAFEBABE); data.writeShort(0); data.writeShort(55); out.closeEntry();
            }
            try { DriverCompatibility.requireJava8(file.toString()); fail("Java 11 driver accepted"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("Java 8")); }
        } finally { Files.delete(file); }
    }
}
