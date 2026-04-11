package com.ticketsync.util;

import javafx.beans.property.ReadOnlyBooleanProperty;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DatabaseHealthMonitor}.
 *
 * <p>No database connection or JavaFX toolkit is required. A stub
 * {@link Connection} is built with JDK {@link Proxy} to intercept the
 * {@code SELECT 1} health-check query. {@code Runnable::run} is injected as
 * the UI runner so that {@code Platform.runLater} callbacks execute
 * synchronously on the test thread (same pattern as {@code SeatSyncServiceTest}).
 */
class DatabaseHealthMonitorTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DatabaseHealthMonitor monitor(DatabaseHealthMonitor.ConnectionFactory cf) {
        return new DatabaseHealthMonitor(cf, Runnable::run);
    }

    /** Calls the private {@code checkDatabaseHealth()} method reflectively. */
    private static void invokeCheck(DatabaseHealthMonitor m) throws Exception {
        Method method = DatabaseHealthMonitor.class.getDeclaredMethod("checkDatabaseHealth");
        method.setAccessible(true);
        method.invoke(m);
    }

    /** Builds a stub {@link Connection} whose {@code SELECT 1} query succeeds. */
    private static Connection successConnection() {
        ResultSet stubRs = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> {
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });

        Statement stubStmt = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[] { Statement.class },
                (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) return stubRs;
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) return stubStmt;
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void checkHealth_successfulQuery_setsDatabaseConnectedTrue() throws Exception {
        DatabaseHealthMonitor m = monitor(() -> successConnection());
        invokeCheck(m);
        assertTrue(m.isConnected(), "databaseConnected should be true after a successful SELECT 1");
    }

    @Test
    void checkHealth_sqlException_setsDatabaseConnectedFalse() throws Exception {
        DatabaseHealthMonitor m = monitor(() -> { throw new SQLException("test failure"); });
        invokeCheck(m);
        assertFalse(m.isConnected(), "databaseConnected should be false when SELECT 1 throws SQLException");
    }

    @Test
    void connectedProperty_returnsReadOnlyView() throws Exception {
        DatabaseHealthMonitor m = monitor(() -> successConnection());
        ReadOnlyBooleanProperty prop = m.connectedProperty();
        assertNotNull(prop);
        // Verify the declared return type is ReadOnlyBooleanProperty (not writable BooleanProperty)
        Method method = DatabaseHealthMonitor.class.getDeclaredMethod("connectedProperty");
        assertEquals(ReadOnlyBooleanProperty.class, method.getReturnType(),
                "connectedProperty() must declare ReadOnlyBooleanProperty as its return type");
    }

    @Test
    void isConnected_reflectsCurrentState() throws Exception {
        DatabaseHealthMonitor m = monitor(() -> { throw new SQLException("test failure"); });
        invokeCheck(m);
        assertEquals(m.connectedProperty().get(), m.isConnected(),
                "isConnected() must return the same value as connectedProperty().get()");
    }

    @Test
    void getInstance_returnsSameInstance() {
        assertSame(DatabaseHealthMonitor.getInstance(), DatabaseHealthMonitor.getInstance(),
                "getInstance() must always return the same singleton instance");
    }
}
