package cn.zencode.assist.core.webchecks;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ServletRoutesTest {
 private String descriptor(String pattern) {return "<web-app><servlet><servlet-name>s</servlet-name><servlet-class>demo.Login</servlet-class></servlet><servlet-mapping><servlet-name>s</servlet-name><url-pattern>"+pattern+"</url-pattern></servlet-mapping></web-app>";}
 @Test public void exactRouteAndFormReachServlet() {
  Map<String,String> f=new HashMap<>();f.put("src/demo/Login.java","");f.put("web/WEB-INF/web.xml",descriptor("/login"));f.put("web/a.html","<form action='/login'><input name='user'></form>");
  assertEquals("src/demo/Login.java",ServletRoutes.exactRoutes(descriptor("/login"),f).get("/login"));
  assertEquals(1,ProjectWebChecks.check("web/a.html",f,Collections.singletonMap("src/demo/Login.java",Collections.singleton("password"))).size());
 }
 @Test public void wildcardAndAmbiguousSourcesDeclined() {
  Map<String,String> f=new HashMap<>();f.put("src/demo/Login.java","");
  assertTrue(ServletRoutes.exactRoutes(descriptor("/*"),f).isEmpty());
  f.put("other/demo/Login.java","");assertTrue(ServletRoutes.exactRoutes(descriptor("/login"),f).isEmpty());
 }
 @Test public void externalEntitiesRejected() {assertTrue(ServletRoutes.exactRoutes("<!DOCTYPE web-app SYSTEM 'file:///etc/passwd'><web-app/>",Collections.emptyMap()).isEmpty());}
}
