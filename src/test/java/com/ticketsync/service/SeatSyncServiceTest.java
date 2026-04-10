package com.ticketsync.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SeatSyncService}.
 *
 * <p>No database connection or JavaFX toolkit is required. A stub {@link Connection}
 * and {@link PGConnection} are built with JDK {@link Proxy} to intercept and record
 * lifecycle calls. {@code Runnable::run} is injected as the UI runner so callbacks
 * execute synchronously on the test thread (no {@code Platform.runLater} dispatch).
 *
 * <p>The stub {@code PGConnection.getNotifications()} is backed by an
 * {@link AtomicReference} that the test can populate before the polling thread reads it.
 */
class SeatSyncServiceTest {

    private boolean listenExecuted;
    private boolean autoCommitSet;
    private boolean connectionClosed;
    /** Notifications to return on the next call to stubPgConn.getNotifications(). */
    private final AtomicReference<PGNotification[]> pendingNotifications = new AtomicReference<>(null);
    private Connection stubConn;
    private SeatSyncService service;

    @BeforeEach
    void setUp() {
        listenExecuted    = false;
        autoCommitSet     = false;
        connectionClosed  = false;
        pendingNotifications.set(null);
        stubConn = buildStubConnection();
        service  = new SeatSyncService(() -> stubConn, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        service.stopListening();
    }

    // -----------------------------------------------------------------------
    // startListening — connection setup
    // -----------------------------------------------------------------------

    @Test
    void startListening_executesListenStatement() {
        service.startListening(id -> {});
        assertTrue(listenExecuted, "LISTEN seat_update must be executed");
    }

    @Test
    void startListening_setsAutoCommitTrue() {
        service.startListening(id -> {});
        assertTrue(autoCommitSet, "setAutoCommit(true) must be called before LISTEN");
    }

    // -----------------------------------------------------------------------
    // stopListening
    // -----------------------------------------------------------------------

    @Test
    void stopListening_closesConnection() {
        service.startListening(id -> {});
        service.stopListening();
        assertTrue(connectionClosed, "connection must be closed on stop");
    }

    @Test
    void stopListening_whenNotStarted_isIdempotent() {
        assertDoesNotThrow(() -> service.stopListening());
    }

    // -----------------------------------------------------------------------
    // notification dispatch (via getNotifications() polling path)
    // -----------------------------------------------------------------------

    @Test
    void notificationCallback_validPayload_invokesConsumer() throws Exception {
        List<Integer> received = new ArrayList<>();
        service.startListening(received::add);

        pendingNotifications.set(new PGNotification[] { stubNotification("seat_update", "42") });

        // Wait up to 2 seconds for the polling thread to pick up the notification
        long deadline = System.currentTimeMillis() + 2000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertEquals(List.of(42), received, "callback must be invoked with parsed seatId=42");
    }

    @Test
    void notificationCallback_wrongChannel_doesNotInvokeCallback() throws Exception {
        List<Integer> received = new ArrayList<>();
        service.startListening(received::add);

        pendingNotifications.set(new PGNotification[] { stubNotification("other_channel", "42") });

        Thread.sleep(700); // longer than one poll interval
        assertTrue(received.isEmpty(), "callback must NOT fire for unrelated channels");
    }

    @Test
    void notificationCallback_malformedPayload_logsAndIgnores() throws Exception {
        service.startListening(id -> fail("must not invoke callback on bad payload"));
        pendingNotifications.set(new PGNotification[] { stubNotification("seat_update", "not-a-number") });

        // Should not throw — malformed payload is silently ignored (WARN log only)
        Thread.sleep(700);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static PGNotification stubNotification(String channel, String payload) {
        return new PGNotification() {
            @Override public String getName()      { return channel; }
            @Override public String getParameter() { return payload; }
            @Override public int    getPID()       { return 1; }
        };
    }

    /**
     * Builds a JDK-proxy {@link Connection} that:
     * <ul>
     *   <li>Records {@code setAutoCommit(true)} → {@code autoCommitSet = true}</li>
     *   <li>Records {@code close()} → {@code connectionClosed = true}</li>
     *   <li>Returns a stub {@link Statement} from {@code createStatement()} that records
     *       {@code execute("LISTEN seat_update")} → {@code listenExecuted = true}</li>
     *   <li>Returns a {@link PGConnection} proxy from {@code unwrap(PGConnection.class)} whose
     *       {@code getNotifications()} returns the value from {@code pendingNotifications}
     *       (one-shot: resets to {@code null} after each call)</li>
     * </ul>
     */
    private Connection buildStubConnection() {
        PGConnection stubPgConn = (PGConnection) Proxy.newProxyInstance(
                PGConnection.class.getClassLoader(),
                new Class<?>[] { PGConnection.class },
                (proxy, method, args) -> {
                    if ("getNotifications".equals(method.getName())) {
                        return pendingNotifications.getAndSet(null);
                    }
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> {
                            if (args != null && Boolean.TRUE.equals(args[0])) autoCommitSet = true;
                        }
                        case "close"    -> connectionClosed = true;
                        case "isClosed" -> { return false; }
                        case "unwrap"   -> {
                            if (args != null && PGConnection.class.equals(args[0])) {
                                return stubPgConn;
                            }
                        }
                        case "createStatement" -> {
                            return (Statement) Proxy.newProxyInstance(
                                    Statement.class.getClassLoader(),
                                    new Class<?>[] { Statement.class },
                                    (p2, m2, a2) -> {
                                        if ("execute".equals(m2.getName()) && a2 != null && a2.length > 0) {
                                            String sql = (String) a2[0];
                                            if ("LISTEN seat_update".equals(sql)) listenExecuted = true;
                                        }
                                        Class<?> r2 = m2.getReturnType();
                                        if (r2 == boolean.class) return false;
                                        if (r2 == int.class)     return 0;
                                        return null;
                                    });
                        }
                    }
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });
    }
}
