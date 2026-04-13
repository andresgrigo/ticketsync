package com.ticketsync.util;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
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
        assertEquals(DatabaseHealthMonitor.RuntimeStatus.HEALTHY, m.runtimeStatusProperty().get());
        assertEquals(0, m.retryAttemptCountProperty().get());
        assertEquals(30L, m.currentCheckIntervalSecondsProperty().get());
    }

    @Test
    void checkHealth_firstSqlException_entersFailSafeMode() throws Exception {
        DatabaseHealthMonitor m = monitor(() -> { throw new SQLException("test failure"); });
        invokeCheck(m);
        assertFalse(m.isConnected(), "databaseConnected should be false when SELECT 1 throws SQLException");
        assertEquals(DatabaseHealthMonitor.RuntimeStatus.FAIL_SAFE, m.runtimeStatusProperty().get());
        assertEquals(1, m.retryAttemptCountProperty().get());
        assertEquals(10L, m.currentCheckIntervalSecondsProperty().get());
    }

    @Test
    void checkHealth_secondConsecutiveFailure_switchesToReconnecting() throws Exception {
        DatabaseHealthMonitor m = monitor(() -> { throw new SQLException("test failure"); });

        invokeCheck(m);
        invokeCheck(m);

        assertFalse(m.isConnected());
        assertEquals(DatabaseHealthMonitor.RuntimeStatus.RECONNECTING, m.runtimeStatusProperty().get());
        assertEquals(2, m.retryAttemptCountProperty().get());
        assertEquals(10L, m.currentCheckIntervalSecondsProperty().get());
    }

    @Test
    void checkHealth_recovery_resetsRetryState() throws Exception {
        DatabaseHealthMonitor m = monitor(new DatabaseHealthMonitor.ConnectionFactory() {
            private int calls;

            @Override
            public Connection get() throws SQLException {
                calls++;
                if (calls < 3) {
                    throw new SQLException("test failure");
                }
                return successConnection();
            }
        });

        invokeCheck(m);
        invokeCheck(m);
        invokeCheck(m);

        assertTrue(m.isConnected());
        assertEquals(DatabaseHealthMonitor.RuntimeStatus.HEALTHY, m.runtimeStatusProperty().get());
        assertEquals(0, m.retryAttemptCountProperty().get());
        assertEquals(30L, m.currentCheckIntervalSecondsProperty().get());
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
    void runtimeStateProperties_returnReadOnlyViews() throws Exception {
        DatabaseHealthMonitor m = monitor(() -> successConnection());

        ReadOnlyObjectProperty<DatabaseHealthMonitor.RuntimeStatus> statusProperty = m.runtimeStatusProperty();
        ReadOnlyIntegerProperty retryAttemptsProperty = m.retryAttemptCountProperty();
        ReadOnlyLongProperty intervalProperty = m.currentCheckIntervalSecondsProperty();

        assertNotNull(statusProperty);
        assertNotNull(retryAttemptsProperty);
        assertNotNull(intervalProperty);
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
