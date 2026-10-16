package cn.zencode.assist.core.database;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;
public class SqlCompletionTest {
    private SqlCompletion.Context context(String text,String ext) { return SqlCompletion.context(text,text.length(),ext); }
    @Test public void sqlAndEmbeddedStringsQualify() {
        assertEquals("us",context("SELECT * FROM us","sql").prefix);
        assertEquals("na",context("String q = \"SELECT na","java").prefix);
        assertNotNull(context("<% String q = \"SELECT na","jsp"));
        assertNull(context("String greeting = \"hello na","java"));
        assertNull(context("// String q = \"SELECT na","java"));
        assertNull(context("String q = \"SELECT x\"; na","java"));
        assertNull(context("SELECT na","txt"));
    }
    @Test public void valuesAndCommentsNeverQualify() {
        assertNull(context("SELECT * FROM users WHERE name = 'us","sql"));
        assertNull(context("SELECT -- us","sql"));
        assertNull(context("SELECT /* us","sql"));
        assertNotNull(context("SELECT /* comment */ us","sql"));
        assertNull(context("SELECT 'it''s us","sql"));
    }
    @Test public void tablePositionExcludesColumnsAndSnapshotIsOnlyAuthority() {
        List<DatabaseCatalog.Entry> snapshot=Arrays.asList(new DatabaseCatalog.Entry("users","","TABLE"),new DatabaseCatalog.Entry("users","user_id","INTEGER"));
        assertEquals(Arrays.asList("users"),SqlCompletion.candidates(snapshot,context("SELECT * FROM us","sql")));
        assertEquals(Arrays.asList("user_id","users"),SqlCompletion.candidates(snapshot,context("SELECT us","sql")));
        assertTrue(SqlCompletion.candidates(snapshot,context("SELECT password","sql")).isEmpty());
        assertEquals(14,context("SELECT * FROM us","sql").start);
    }
}
