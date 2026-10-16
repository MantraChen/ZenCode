package cn.zencode.assist.core.webchecks;
import org.junit.Test;
import static org.junit.Assert.*;
public class WebChecksTest {
    @Test public void findsStaticDuplicates() {
        assertTrue(WebChecks.check("html", "<div id='a'></div><p id=\"a\">").get(0).startsWith("重复静态 id"));
    }
    @Test public void ignoresCommentsScriptsAndAttributeStrings() {
        assertTrue(WebChecks.check("html", "<!-- <p id='a'> --><script>var x=\"<p id='a'>\";</script><p title=\"id='a'\" id='a'>").get(0).startsWith("未发现"));
    }
    @Test public void skipsDynamicAndOtherLanguages() {
        assertTrue(WebChecks.check("jsp", "${x}<p id='a'><p id='a'>").get(0).contains("动态模板"));
        assertTrue(WebChecks.check("js", "'<p id=a><p id=a>'").get(0).contains("尚未检查"));
    }
    @Test public void boundsInput() {
        assertTrue(WebChecks.check("html", new String(new char[WebChecks.MAX_LENGTH + 1])).get(0).contains("超过"));
    }
}
