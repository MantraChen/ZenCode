package cn.zencode.assist.core;
import cn.zencode.assist.core.runtime.OfflineWebTemplate;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.nio.file.*;
public class OfflineWebTemplateTest {
 @Rule public TemporaryFolder folder = new TemporaryFolder();
 @Test public void exportsSourcesWithoutDriversOrCredentials() throws Exception {
  Path path = folder.getRoot().toPath().resolve("sample");
  OfflineWebTemplate.create(path);
  assertTrue(Files.exists(path.resolve("WebContent/index.jsp")));
  assertTrue(Files.exists(path.resolve("WebContent/WEB-INF/web.xml")));
  String source = new String(Files.readAllBytes(path.resolve("src/example/JdbcSample.java")), "UTF-8");
  assertTrue(source.contains("PreparedStatement"));
  assertTrue(source.contains("setQueryTimeout(10)"));
  assertFalse(Files.exists(path.resolve("WebContent/WEB-INF/lib")));
 }
 @Test public void rejectsExistingDirectoryWithoutTouchingFiles() throws Exception {
  Path path = folder.newFolder().toPath();
  Path marker = path.resolve("marker"); Files.write(marker, new byte[]{42});
  try { OfflineWebTemplate.create(path); fail(); } catch (FileAlreadyExistsException expected) { }
  assertArrayEquals(new byte[]{42}, Files.readAllBytes(marker));
  assertFalse(Files.exists(path.resolve("src")));
 }
}
