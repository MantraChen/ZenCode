package cn.zencode.assist.core.database;

import java.sql.*;
import java.util.*;

/** Session-only metadata snapshot. Never executes application SQL. */
public final class DatabaseCatalog {
    public static final String[] NAMES = {"自定义 / MF（厂商待确认）", "MySQL 8", "PostgreSQL", "SQL Server", "Oracle", "H2"};
    private static final String[] DRIVERS = {"", "com.mysql.cj.jdbc.Driver", "org.postgresql.Driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "oracle.jdbc.OracleDriver", "org.h2.Driver"};
    private static final String[] URLS = {"jdbc:", "jdbc:mysql://localhost:3306/database", "jdbc:postgresql://localhost:5432/database", "jdbc:sqlserver://localhost:1433;databaseName=database", "jdbc:oracle:thin:@localhost:1521/service", "jdbc:h2:mem:zencode"};
    public static String driver(int index) { return DRIVERS[index]; }
    public static String url(int index) { return URLS[index]; }
    public static final class Entry {
        public final String table, column, type;
        public Entry(String table, String column, String type) { this.table = table; this.column = column; this.type = type; }
        public String candidate() { return column.isEmpty() ? table : column; }
    }
    public static List<Entry> read(Connection connection) throws SQLException {
        List<Entry> entries = new ArrayList<Entry>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
            int count = 0;
            while (tables.next() && count++ < 100) {
                String catalog = tables.getString("TABLE_CAT"), schema = tables.getString("TABLE_SCHEM"), name = tables.getString("TABLE_NAME");
                String qualified = schema == null || schema.isEmpty() ? name : schema + "." + name;
                entries.add(new Entry(qualified, "", tables.getString("TABLE_TYPE")));
                String escaped = escape(name, metadata.getSearchStringEscape());
                try (ResultSet columns = metadata.getColumns(catalog, schema == null ? null : escape(schema, metadata.getSearchStringEscape()), escaped, "%")) {
                    int n = 0;
                    while (columns.next() && n++ < 200) entries.add(new Entry(qualified, columns.getString("COLUMN_NAME"), columns.getString("TYPE_NAME")));
                }
            }
        }
        return Collections.unmodifiableList(entries);
    }
    static String escape(String value, String escape) {
        if (escape == null || escape.isEmpty()) return value;
        return value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }
    public static List<Entry> candidates(List<Entry> entries, String prefix) {
        List<Entry> found = new ArrayList<Entry>();
        for (Entry entry : entries) if (entry.candidate().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) found.add(entry);
        return found;
    }
    public static String preparedExample() {
        return "// 替换为项目中已验证的连接和表列名；不要拼接用户输入。\nString sql = \"SELECT name FROM example_table WHERE id = ?\";\ntry (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {\n    ps.setLong(1, 1L);\n    try (java.sql.ResultSet rs = ps.executeQuery()) {\n        while (rs.next()) {\n            String name = rs.getString(1);\n        }\n    }\n}";
    }
    private DatabaseCatalog() { }
}
