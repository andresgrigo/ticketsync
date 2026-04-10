package com.ticketsync.service;

import com.ticketsync.util.DatabaseConfig;
import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Listens for PostgreSQL LISTEN/NOTIFY notifications on the {@code seat_update} channel
 * and dispatches seat ID updates to a registered callback on the JavaFX Application Thread.
 *
 * <p>A single dedicated connection is held permanently for the lifetime of the service.
 * This connection is never used for business queries — only for LISTEN/NOTIFY.
 *
 * <p>A background daemon thread polls the connection every 500ms via {@code SELECT 1}.
 * After each poll, pending {@link PGNotification}s are retrieved via
 * {@link PGConnection#getNotifications()} and dispatched to the registered callback.
 * PGJDBC delivers notifications synchronously during statement execution, so polling
 * is required to pick them up.
 */
public class SeatSyncService {

    private static final Logger LOGGER = LogManager.getLogger(SeatSyncService.class);
    private static final String CHANNEL = "seat_update";
    private static final int RECONNECT_DELAY_SECONDS = 5;
    private static final int POLL_INTERVAL_MS = 500;

    private final ConnectionFactory connFactory;
    private final Consumer<Runnable> uiRunner;

    private Connection listenConnection;
    private PGConnection pgListenConnection;
    private ScheduledExecutorService executor;
    private Consumer<Integer> seatUpdateCallback;
    private volatile boolean running;

    /** Production constructor — uses live DB connection and JavaFX Platform.runLater. */
    public SeatSyncService() {
        this.connFactory = DatabaseConfig::getConnection;
        this.uiRunner = Platform::runLater;
    }

    /**
     * Test constructor — allows injecting a stub connection factory and a synchronous
     * UI runner ({@code Runnable::run}) to avoid requiring DB or JavaFX toolkit in tests.
     */
    SeatSyncService(ConnectionFactory connFactory, Consumer<Runnable> uiRunner) {
        this.connFactory = connFactory;
        this.uiRunner = uiRunner;
    }

    /**
     * Opens a dedicated LISTEN connection, executes {@code LISTEN seat_update},
     * and starts the background polling thread.
     *
     * @param callback invoked on the JavaFX thread with the updated seat ID
     */
    public void startListening(Consumer<Integer> callback) {
        this.seatUpdateCallback = callback;
        this.running = true;
        try {
            openListenConnection();
            startPollingThread();
            LOGGER.info("SeatSyncService started listening on channel '{}'", CHANNEL);
        } catch (SQLException e) {
            this.running = false;
            LOGGER.error("Failed to start SeatSyncService LISTEN connection", e);
        }
    }

    private void openListenConnection() throws SQLException {
        listenConnection = connFactory.get();
        listenConnection.setAutoCommit(true);
        pgListenConnection = listenConnection.unwrap(PGConnection.class);
        try (Statement stmt = listenConnection.createStatement()) {
            stmt.execute("LISTEN " + CHANNEL);
            LOGGER.debug("LISTEN {} registered on connection", CHANNEL);
        }
    }

    private void startPollingThread() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SeatSync-Listener");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::pollForNotifications, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void pollForNotifications() {
        if (!running) {
            return;
        }
        try (Statement stmt = listenConnection.createStatement()) {
            stmt.execute("SELECT 1");
            PGNotification[] notifications = pgListenConnection.getNotifications();
            if (notifications != null) {
                for (PGNotification n : notifications) {
                    if (CHANNEL.equals(n.getName())) {
                        dispatchNotification(n.getParameter());
                    }
                }
            }
        } catch (SQLException e) {
            if (running) {
                LOGGER.warn("LISTEN connection lost, scheduling reconnect: {}", e.getMessage());
                reconnect();
            }
        }
    }

    private void dispatchNotification(String payload) {
        try {
            int seatId = Integer.parseInt(payload);
            uiRunner.accept(() -> seatUpdateCallback.accept(seatId));
            LOGGER.debug("Seat update notification received: seatId={}", seatId);
        } catch (NumberFormatException e) {
            LOGGER.warn("Received malformed seat_update payload: '{}' — ignored", payload);
        }
    }

    private void reconnect() {
        closeConnectionQuietly(listenConnection);
        listenConnection = null;
        pgListenConnection = null;
        int attempt = 0;
        while (running) {
            attempt++;
            try {
                Thread.sleep(RECONNECT_DELAY_SECONDS * 1000L);
                openListenConnection();
                LOGGER.info("SeatSyncService reconnected on attempt {}", attempt);
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (SQLException e) {
                LOGGER.warn("Reconnect attempt {} failed: {}", attempt, e.getMessage());
            }
        }
    }

    /**
     * Shuts down the polling thread and closes the dedicated LISTEN connection.
     * Safe to call when the service has not been started (idempotent).
     */
    public void stopListening() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        closeConnectionQuietly(listenConnection);
        listenConnection = null;
        pgListenConnection = null;
        LOGGER.info("SeatSyncService stopped");
    }

    private void closeConnectionQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.warn("Error closing LISTEN connection: {}", e.getMessage());
            }
        }
    }
}
