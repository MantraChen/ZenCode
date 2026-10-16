package cn.zencode.assist.core;
import cn.zencode.assist.core.runtime.TomcatInstallation;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.util.jar.*;

public class TomcatInstallationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File installation(String version) throws Exception {
        File home = temporary.newFolder();
        for (String path : new String[]{"conf/server.xml", "bin/bootstrap.jar", "lib/servlet-api.jar"}) {
            File file = new File(home, path); file.getParentFile().mkdirs(); assertTrue(file.createNewFile());
        }
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(new File(home,"lib/catalina.jar")))) {
            out.putNextEntry(new JarEntry("org/apache/catalina/util/ServerInfo.properties"));
            out.write(("server.number="+version+"\n").getBytes("ISO-8859-1")); out.closeEntry();
        }
        return home;
    }
    @Test public void acceptsSupportedFamiliesFromJar() throws Exception {
        for (String v : new String[]{"7.0.109.0", "8.0.53.0", "8.5.100.0", "9.0.98.0"})
            assertTrue(TomcatInstallation.inspect(installation(v)).contains(v));
    }
    @Test public void rejectsJakartaAndUnknownVersions() throws Exception {
        for (String v : new String[]{"10.1.0.0", "11.0.0", "", "9.9.1"}) {
            try { TomcatInstallation.inspect(installation(v)); fail(v); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("版本")); }
        }
    }
    @Test public void rejectsIncompleteInstallation() throws Exception {
        try { TomcatInstallation.inspect(temporary.newFolder()); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("server.xml")); }
    }
}
