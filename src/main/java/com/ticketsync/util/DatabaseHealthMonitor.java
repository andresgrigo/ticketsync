package com.ticketsync.util;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Monitors database connectivity and exposes a {@link ReadOnlyBooleanProperty}
 * that reflects the current connection health.
 *
 * <p>A background daemon thread executes {@code SELECT 1} every 30 seconds.
 * If the query fails, the monitor switches to a 10-second retry interval for
 * faster recovery (NFR-E02). Once connectivity is restored, the interval
 * returns to 30 seconds and normal operations resume automatically.
 *
 * <p>ViewModels bind their {@code purchaseEnabled} property to
 * {@link #connectedProperty()} so that all purchase actions are automatically
 * disabled when the database goes offline (fail-safe mode, NFR-E01, UX-DR08).
 *
 * <p>This class is an eager static singleton. Obtain the shared instance via
 * {@link #getInstance()} and call {@link #start()} once after the initial DB
 * connection succeeds (in {@code App.start()}). Call {@link #shutdown()} in
 * {@code App.stop()} before closing the connection pool.
 */
public class DatabaseHealthMonitor {

    private static final Logger LOGGER = LogManager.getLogger(DatabaseHealthMonitor.class);

    private static final long HEALTHY_INTERVAL_SECONDS = 30;
    private static final long RETRY_INTERVAL_SECONDS   = 10;

    /** Eager static singleton — initialized once at class-load time. */
    private static final DatabaseHealthMonitor INSTANCE = new DatabaseHealthMonitor();

    @FunctionalInterface
    interface ConnectionFactory {
        Connection get() throws SQLException;
    }

    private final ConnectionFactory connFactory;
    private final Consumer<Runnable> uiRunner;

    private final ReadOnlyBooleanWrapper databaseConnected = new ReadOnlyBooleanWrapper(true);

    private ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> scheduledTask;
    private volatile boolean lastCheckFailed = false;

    /** Production constructor — uses live DB and JavaFX {@code Platform.runLater}. */
    private DatabaseHealthMonitor() {
        this(DatabaseConfig::getConnection, Platform::runLater);
    }

    /**
     * Test constructor — allows injecting a stub connection factory and a
     * synchronous UI runner ({@code Runnable::run}) so that unit tests run
     * without a live database or JavaFX toolkit.
     */
    DatabaseHealthMonitor(ConnectionFactory connFactory, Consumer<Runnable> uiRunner) {
        this.connFactory = connFactory;
        this.uiRunner    = uiRunner;
    }

    /** Returns the shared singleton instance. */
    public static DatabaseHealthMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * Starts the background health-check scheduler with an initial 30-second interval.
     * Call this from {@code App.start()} after the initial DB connection test passes.
     */
    public void start() {
        scheduler     = createScheduler();
        scheduledTask = scheduler.scheduleAtFixedRate(
                this::checkDatabaseHealth, 0, HEALTHY_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOGGER.info("DatabaseHealthMonitor started (check interval: {}s)", HEALTHY_INTERVAL_SECONDS);
    }

    /**
     * Stops the background scheduler.
     * Call this from {@code App.stop()} before shutting down the connection pool.
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            LOGGER.info("DatabaseHealthMonitor shutdown");
        }
    }

    /**
     * Returns a read-only view of the database-connected property.
     * External callers may observe the value but cannot mutate it via this handle.
     */
    public ReadOnlyBooleanProperty connectedProperty() {
        return databaseConnected.getReadOnlyProperty();
    }

    /** Convenience boolean accessor — equivalent to {@code connectedProperty().get()}. */
    public boolean isConnected() {
        return databaseConnected.get();
    }

    private void checkDatabaseHealth() {
        boolean healthy = false;
        try (Connection conn = connFactory.get()) {
            conn.createStatement().executeQuery("SELECT 1").close();
            healthy = true;
            if (lastCheckFailed) {
                LOGGER.info("Database connection restored");
            } else {
                LOGGER.debug("Database health check passed");
            }
        } catch (SQLException e) {
            LOGGER.error("Database health check failed: {}", e.getMessage());
        }
        final boolean h = healthy;
        uiRunner.accept(() -> databaseConnected.set(h));
        adjustSchedule(healthy);
    }

    /**
     * Reschedules the health check with the appropriate interval on state
     * transitions (healthy → failed or failed → recovered). No-ops when the
     * state is unchanged or the scheduler has not been started / is shut down.
     */
    private void adjustSchedule(boolean healthy) {
        // stateChanged is true when: lastCheckFailed=false & healthy=false (new failure)
        //                         or: lastCheckFailed=true  & healthy=true  (recovery)
        boolean stateChanged = (healthy == lastCheckFailed);
        if (!stateChanged) return;

        lastCheckFailed = !healthy;

        if (scheduler == null || scheduler.isShutdown()) return;

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        long interval = healthy ? HEALTHY_INTERVAL_SECONDS : RETRY_INTERVAL_SECONDS;
        scheduledTask = scheduler.scheduleAtFixedRate(
                this::checkDatabaseHealth, interval, interval, TimeUnit.SECONDS);
        LOGGER.info("Health check interval changed to {}s", interval);
    }

    private ScheduledExecutorService createScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DB-HealthMonitor");
            t.setDaemon(true);
            return t;
        });
    }
}
