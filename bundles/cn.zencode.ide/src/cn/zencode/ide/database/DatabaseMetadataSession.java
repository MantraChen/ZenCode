package cn.zencode.ide.database;
import java.util.*;
import cn.zencode.assist.core.database.DatabaseCatalog;
/** Memory-only metadata; contains neither JDBC URL nor credentials. */
public final class DatabaseMetadataSession {
    private static volatile List<DatabaseCatalog.Entry> entries=Collections.emptyList();
    public static List<DatabaseCatalog.Entry> snapshot() { return entries; }
    public static void publish(List<DatabaseCatalog.Entry> value) { entries=Collections.unmodifiableList(new ArrayList<DatabaseCatalog.Entry>(value)); }
    public static void clear() { entries=Collections.emptyList(); }
    private DatabaseMetadataSession() { }
}
