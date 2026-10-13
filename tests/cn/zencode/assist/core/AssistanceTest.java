package cn.zencode.assist.core;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class AssistanceTest {
    @Test public void swappedLettersHaveOneEditCost() {
        assertEquals(1, NameSuggestions.distance("gteParameter", "getParameter"));
        assertEquals(Arrays.asList("getParameter"), NameSuggestions.suggest("gteParameter", Arrays.asList("getParameter", "setAttribute")));
    }
    @Test public void supportsMissingExtraAndWrongCaseLetters() {
        assertEquals(1, NameSuggestions.distance("getParametr", "getParameter"));
        assertEquals(1, NameSuggestions.distance("getParametter", "getParameter"));
        assertEquals(1, NameSuggestions.distance("GetParameter", "getParameter"));
    }
    @Test public void validCustomIdentifierIsNeverCorrected() {
        assertTrue(NameSuggestions.suggest("gte", Arrays.asList("get", "gte")).isEmpty());
    }
    @Test public void doesNotInventOutOfScopeNames() {
        assertTrue(NameSuggestions.suggest("gteParameter", Arrays.asList("size", "clear")).isEmpty());
    }
    @Test public void keepsAmbiguousCandidatesAndRemovesDuplicates() {
        assertEquals(Arrays.asList("get", "set"), NameSuggestions.suggest("fet", Arrays.asList("set", "get", "get")));
    }
    @Test public void boundsSuggestionsAndRejectsOversizedNames() {
        assertEquals(5, NameSuggestions.suggest("a", Arrays.asList("b", "c", "d", "e", "f", "g")).size());
        assertTrue(NameSuggestions.suggest(new String(new char[129]), Arrays.asList("get")).isEmpty());
    }
    @Test public void checksSyntaxButPreservesChineseTextAndComments() {
        String source = "class A { String s = \"你好；（）\"; char c = '；'; // 中文；\n/* 注释＝ */ int n＝1； }";
        List<Diagnostic> errors = new JavaPunctuationCheck().check(source);
        assertEquals(2, errors.size());
        assertEquals(2, errors.get(0).getLine());
        assertEquals("=", errors.get(0).getFix().getReplacement());
    }
    @Test public void escapedQuoteDoesNotExposeStringContents() {
        assertTrue(new JavaPunctuationCheck().check("String s = \"a\\\"；b\";").isEmpty());
    }
    @Test public void malformedLiteralDoesNotTriggerCascadingFixes() {
        assertTrue(new JavaPunctuationCheck().check("String s = \"未结束\n；＝").isEmpty());
    }
    @Test public void unicodeEscapesAreDeferredToCompiler() {
        String source = "// " + "\\" + "u000a int x＝1；";
        assertTrue(new JavaPunctuationCheck().check(source).isEmpty());
    }
    @Test public void crlfAndSupplementaryCharactersKeepCorrectPositions() {
        String source = "// 😀\r\nint n = 1；";
        Diagnostic error = new JavaPunctuationCheck().check(source).get(0);
        assertEquals(2, error.getLine());
        assertEquals(source.indexOf('；'), error.getFix().getOffset());
        assertEquals("// 😀\r\nint n = 1;", error.getFix().apply(source));
    }
    @Test(expected = IllegalStateException.class) public void rejectsStaleFixEvenIfTokenIsStillThere() {
        TextFix fix = new TextFix("gte()", 0, 3, "get");
        fix.apply("gte(); // modified");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsOutOfBoundsEdit() {
        new TextFix("abc", 2, Integer.MAX_VALUE, "x");
    }
    @Test public void immutableSnapshotCanBePreviewed() {
        TextFix fix = new TextFix("request.gteParameter(\"x\")", 8, 12, "getParameter");
        assertEquals("request.getParameter(\"x\")", fix.apply("request.gteParameter(\"x\")"));
    }
}
