package com.ticketsync.util;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
 * Monitorea la conectividad de la base de datos y expone tanto la señal de salud
 * {@link ReadOnlyBooleanProperty} heredada como un modelo de estado de ejecución más
 * rico para mensajería en modo a prueba de fallos del POS.
 *
 * <p>Un hilo daemon en segundo plano ejecuta {@code SELECT 1} cada 30 segundos.
 * Si la consulta falla, el monitor cambia a un intervalo de reintento de 10 segundos
 * para una recuperación más rápida. Una vez que se restaura la conectividad, el intervalo
 * vuelve a 30 segundos y las operaciones normales se reanudan automáticamente.
 *
 * <p>Los ViewModels vinculan su propiedad {@code purchaseEnabled} a
 * {@link #connectedProperty()} para que todas las acciones de compra se deshabiliten
 * automáticamente cuando la base de datos se desconecta (modo a prueba de fallos).
 *
 * <p>Esta clase es un singleton estático eager. Obtenga la instancia compartida mediante
 * {@link #getInstance()} y llame a {@link #start()} una vez después de que la prueba
 * inicial de conexión a la BD sea exitosa (en {@code App.start()}). Llame a {@link #shutdown()}
 * en {@code App.stop()} antes de cerrar el pool de conexiones.
 */
public class DatabaseHealthMonitor {

    private static final Logger LOGGER = LogManager.getLogger(DatabaseHealthMonitor.class);

    private static final long HEALTHY_INTERVAL_SECONDS = 30;
    private static final long RETRY_INTERVAL_SECONDS   = 10;

    /** Singleton estático eager — inicializado una vez en el momento de carga de la clase. */
    private static final DatabaseHealthMonitor INSTANCE = new DatabaseHealthMonitor();

    /**
     * Representa el estado de salud actual en tiempo de ejecución de la conexión a la base de datos.
     */
    public enum RuntimeStatus {
        /** Estado de operación normal; todos los latidos de la base de datos tienen éxito. */
        HEALTHY,
        /** Base de datos inalcanzable; la aplicación está en modo de solo lectura/sin ventas a prueba de fallos. */
        FAIL_SAFE,
        /** El útimo latido falló; reintentando activamente la conexión a intervalo reducido. */
        RECONNECTING
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection get() throws SQLException;
    }

    private final ConnectionFactory connFactory;
    private final Consumer<Runnable> uiRunner;

    private final ReadOnlyBooleanWrapper databaseConnected = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyObjectWrapper<RuntimeStatus> runtimeStatus =
            new ReadOnlyObjectWrapper<>(RuntimeStatus.HEALTHY);
    private final ReadOnlyIntegerWrapper retryAttemptCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyLongWrapper currentCheckIntervalSeconds =
            new ReadOnlyLongWrapper(HEALTHY_INTERVAL_SECONDS);

    private ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> scheduledTask;
    private volatile boolean lastCheckFailed = false;
    private volatile int consecutiveFailureCount = 0;

    /** Constructor de producción — usa BD real y JavaFX {@code Platform.runLater}. */
    private DatabaseHealthMonitor() {
        this(DatabaseConfig::getConnection, Platform::runLater);
    }

    /**
     * Constructor para pruebas — permite inyectar una fábrica de conexiones de prueba y un
     * ejecutor de UI síncrono ({@code Runnable::run}) para que las pruebas unitarias funcionen
     * sin una base de datos real ni el kit de herramientas JavaFX.
     */
    DatabaseHealthMonitor(ConnectionFactory connFactory, Consumer<Runnable> uiRunner) {
        this.connFactory = connFactory;
        this.uiRunner    = uiRunner;
    }

    /**
     * Devuelve la instancia singleton compartida.
     *
     * @return la instancia {@code DatabaseHealthMonitor} de toda la aplicación
     */
    public static DatabaseHealthMonitor getInstance() {
        return INSTANCE;
    }

    /**
     * Inicia el planificador de verificación de salud en segundo plano con un intervalo inicial de 30 segundos.
     * Llame este método desde {@code App.start()} después de que la prueba inicial de conexión a la BD sea exitosa.
     */
    public void start() {
        scheduler     = createScheduler();
        scheduledTask = scheduler.scheduleAtFixedRate(
                this::checkDatabaseHealth, 0, HEALTHY_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOGGER.info("DatabaseHealthMonitor started (check interval: {}s)", HEALTHY_INTERVAL_SECONDS);
    }

    /**
     * Detiene el planificador en segundo plano.
     * Llame este método desde {@code App.stop()} antes de apagar el pool de conexiones.
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            LOGGER.info("DatabaseHealthMonitor shutdown");
        }
    }

    /**
     * Devuelve una vista de solo lectura de la propiedad de conexión a la base de datos.
     * Las llamadas externas pueden observar el valor pero no pueden mutarlo a través de este manejador.
     *
     * @return propiedad booleana de solo lectura; {@code true} cuando la base de datos es alcanzable
     */
    public ReadOnlyBooleanProperty connectedProperty() {
        return databaseConnected.getReadOnlyProperty();
    }

    /**
     * Acceso booleano de conveniencia — equivalente a {@code connectedProperty().get()}.
     *
     * @return {@code true} si la última verificación de salud de la base de datos fue exitosa
     */
    public boolean isConnected() {
        return databaseConnected.get();
    }

    /**
     * Devuelve la propiedad del estado de ejecución actual.
     * Reflects whether the monitor is healthy, in fail-safe mode, or reconnecting.
     *
     * @return read-only property holding the current {@link RuntimeStatus}
     */
    public ReadOnlyObjectProperty<RuntimeStatus> runtimeStatusProperty() {
        return runtimeStatus.getReadOnlyProperty();
    }

    /**
     * Returns the number of consecutive failed reconnect attempts since the last failure began.
     *
     * @return read-only integer property; resets to 0 once connectivity is restored
     */
    public ReadOnlyIntegerProperty retryAttemptCountProperty() {
        return retryAttemptCount.getReadOnlyProperty();
    }

    /**
     * Returns the current health-check interval in seconds.
     * Switches between 30 s (healthy) and 10 s (retry) depending on connection state.
     *
     * @return read-only long property representing the active polling interval in seconds
     */
    public ReadOnlyLongProperty currentCheckIntervalSecondsProperty() {
        return currentCheckIntervalSeconds.getReadOnlyProperty();
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
            consecutiveFailureCount = lastCheckFailed ? consecutiveFailureCount + 1 : 1;
            if (lastCheckFailed) {
                LOGGER.warn("Database reconnect attempt {} failed: {}", consecutiveFailureCount, e.getMessage());
            } else {
                LOGGER.error("Database connection lost; entering fail-safe mode: {}", e.getMessage());
            }
        }

        RuntimeStatus nextStatus;
        int nextRetryAttemptCount;
        long nextIntervalSeconds;
        if (healthy) {
            consecutiveFailureCount = 0;
            nextStatus = RuntimeStatus.HEALTHY;
            nextRetryAttemptCount = 0;
            nextIntervalSeconds = HEALTHY_INTERVAL_SECONDS;
        } else {
            nextStatus = consecutiveFailureCount == 1 ? RuntimeStatus.FAIL_SAFE : RuntimeStatus.RECONNECTING;
            nextRetryAttemptCount = consecutiveFailureCount;
            nextIntervalSeconds = RETRY_INTERVAL_SECONDS;
        }

        final boolean connected = healthy;
        final RuntimeStatus runtimeStatusValue = nextStatus;
        final int retryAttemptValue = nextRetryAttemptCount;
        final long intervalValue = nextIntervalSeconds;
        uiRunner.accept(() -> {
            databaseConnected.set(connected);
            runtimeStatus.set(runtimeStatusValue);
            retryAttemptCount.set(retryAttemptValue);
            currentCheckIntervalSeconds.set(intervalValue);
        });
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
