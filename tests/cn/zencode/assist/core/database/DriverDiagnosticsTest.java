package cn.zencode.assist.core.database;
import static org.junit.Assert.*;
import org.junit.Test;
import java.sql.SQLException;
public class DriverDiagnosticsTest {
    @Test public void errorsNeverEchoDriverMessagesOrCredentials() {
        for (String state : new String[]{"28000","08001","0A000","ZZ999",null}) {
            String message=DriverDiagnostics.describe(new SQLException("jdbc:vendor://secret password=hunter2",state));
            assertFalse(message.contains("secret")); assertFalse(message.contains("hunter2")); assertFalse(message.contains("jdbc:"));
        }
        assertTrue(DriverDiagnostics.describe(new SQLException("", "28000")).contains("身份验证"));
        assertTrue(DriverDiagnostics.describe(new SQLException("", "08001")).contains("网络"));
    }
}
