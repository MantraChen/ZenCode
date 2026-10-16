package cn.zencode.assist.core.database;
import static org.junit.Assert.*;
import org.junit.Test;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class MetadataReadTest {
    @SuppressWarnings("unchecked") private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private ResultSet rows(Map<String,String> row, AtomicInteger closes) {
        AtomicInteger position=new AtomicInteger();
        return proxy(ResultSet.class,(object,method,args)-> {
            if(method.getName().equals("next")) return position.getAndIncrement()==0;
            if(method.getName().equals("getString")) return row.get(args[0]);
            if(method.getName().equals("close")) {closes.incrementAndGet();return null;}
            throw new AssertionError("Unexpected operation: "+method.getName());
        });
    }
    @Test public void readsMetadataOnlyEscapesPatternsAndClosesBothResults() throws Exception {
        AtomicInteger closes=new AtomicInteger();
        Map<String,String> table=new HashMap<String,String>(); table.put("TABLE_CAT","catalog");table.put("TABLE_SCHEM","public");table.put("TABLE_NAME","a_b");table.put("TABLE_TYPE","TABLE");
        Map<String,String> column=new HashMap<String,String>();column.put("COLUMN_NAME","id");column.put("TYPE_NAME","INTEGER");
        DatabaseMetaData metadata=proxy(DatabaseMetaData.class,(object,method,args)-> {
            if(method.getName().equals("getTables")) return rows(table,closes);
            if(method.getName().equals("getSearchStringEscape")) return "\\";
            if(method.getName().equals("getColumns")) { assertEquals("a\\_b",args[2]); return rows(column,closes); }
            throw new AssertionError("Unexpected operation: "+method.getName());
        });
        Connection connection=proxy(Connection.class,(object,method,args)-> {
            if(method.getName().equals("getMetaData")) return metadata;
            throw new AssertionError("SQL must not be executed: "+method.getName());
        });
        List<DatabaseCatalog.Entry> snapshot=DatabaseCatalog.read(connection);
        assertEquals(2,snapshot.size()); assertEquals("public.a_b",snapshot.get(0).table);assertEquals("id",snapshot.get(1).column);assertEquals(2,closes.get());
        try {snapshot.clear();fail("snapshot must be immutable");}catch(UnsupportedOperationException expected) { }
    }
}
