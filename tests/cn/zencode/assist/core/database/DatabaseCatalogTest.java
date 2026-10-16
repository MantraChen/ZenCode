package cn.zencode.assist.core.database;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class DatabaseCatalogTest {
    @Test public void templatesRemainExplicitAndUnknownVendorHasNoDriver() {
        assertEquals("", DatabaseCatalog.driver(0));
        assertEquals("jdbc:", DatabaseCatalog.url(0));
        for (int i=1; i<DatabaseCatalog.NAMES.length; i++) { assertFalse(DatabaseCatalog.driver(i).isEmpty()); assertTrue(DatabaseCatalog.url(i).startsWith("jdbc:")); }
    }
    @Test public void candidatesOnlyUseSnapshotAndCaseInsensitivePrefix() {
        List<DatabaseCatalog.Entry> entries = Arrays.asList(new DatabaseCatalog.Entry("users", "", "TABLE"), new DatabaseCatalog.Entry("users", "UserId", "BIGINT"));
        assertEquals(2, DatabaseCatalog.candidates(entries, "US").size());
        assertEquals(1, DatabaseCatalog.candidates(entries, "USERI").size());
        assertEquals(0, DatabaseCatalog.candidates(entries, "password").size());
    }
    @Test public void escapesMetadataPatternWildcards() { assertEquals("a\\_b\\%", DatabaseCatalog.escape("a_b%", "\\")); }
    @Test public void exampleBindsValuesAndClosesResources() { String text = DatabaseCatalog.preparedExample(); assertTrue(text.contains("WHERE id = ?")); assertTrue(text.contains("ps.setLong")); assertTrue(text.contains("try (java.sql.ResultSet")); }
}
