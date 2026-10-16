package cn.zencode.assist.core.webchecks;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ProjectWebChecksTest {
 @Test public void missingAssetAndExclusions() {
  Map<String,String> f=new HashMap<String,String>();
  f.put("web/a.html","<!-- <img src='bad'> --><img src='ok.png'><img src='missing.png'><img src='https://a/b'><img src='${x}'>");
  f.put("web/ok.png","");
  assertEquals(1,ProjectWebChecks.check("web/a.html",f).size());
 }
 @Test public void explicitFormSuggestion() {
  Map<String,String> f=new HashMap<String,String>();
  f.put("a.html","<form action='b.jsp'><input name='user'></form>");
  f.put("b.jsp","<% request.getParameter(\"user\"); request.getParameter(\"pass\"); %>");
  assertEquals(1,ProjectWebChecks.check("a.html",f,Collections.singletonMap("b.jsp",new HashSet<String>(Arrays.asList("user","pass")))).size());
 }
 @Test public void boundedRelativeResolution() {
  assertNull(ProjectWebChecks.resolve("a.html","../x"));
  assertNull(ProjectWebChecks.resolve("a.html","/x"));
  assertNull(ProjectWebChecks.resolve("a.html","x?y"));
  assertEquals("x",ProjectWebChecks.resolve("web/a.html","../x"));
 }
 @Test public void cssCandidatesOnlyFromLinkedSimpleDeclarations() {
  Map<String,String> f=new HashMap<String,String>();
  f.put("a.html","<link rel='stylesheet' href='a.css'><div class='primry exact'>");
  f.put("a.css","/* .primry {} */ .primary {} .exact {} .nested .other {} .x {content: '.primry {}';}");
  assertEquals(1,ProjectWebChecks.check("a.html",f).size());
  f.put("a.html","<link rel='stylesheet' href='https://x/a.css'><div class='primry'>");
  assertTrue(ProjectWebChecks.check("a.html",f).isEmpty());
 }
 @Test public void textualRequestNeverProvesBinding() {
  Map<String,String> f=new HashMap<String,String>();
  f.put("a.html","<form action='b.jsp'></form>");
  f.put("b.jsp","<% request.getParameter(\"typo\"); %>");
  assertTrue(ProjectWebChecks.check("a.html",f).isEmpty());
 }
}
