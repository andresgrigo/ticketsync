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
 * Escucha notificaciones PostgreSQL LISTEN/NOTIFY en el canal {@code seat_update}
 * y despacha actualizaciones de ID de asiento a un callback registrado en el Hilo de Aplicación JavaFX.
 *
 * <p>Se mantiene una sola conexión dedicada permanentemente durante la vida útil del servicio.
 * Esta conexión nunca se usa para consultas de negocio — solo para LISTEN/NOTIFY.
 *
 * <p>Un hilo daemon en segundo plano sondea la conexión cada 500ms vía {@code SELECT 1}.
 * Tras cada sondeo, se recuperan las {@link PGNotification}s pendientes vía
 * {@link PGConnection#getNotifications()} y se despachan al callback registrado.
 * PGJDBC entrega notificaciones sincónicamente durante la ejecución de sentencias, por lo que
 * se requiere sondeo para recogerlas.
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

    /** Constructor de producción — usa conexión BD activa y JavaFX Platform.runLater. */
    public SeatSyncService() {
        this.connFactory = DatabaseConfig::getConnection;
        this.uiRunner = Platform::runLater;
    }

    /**
     * Constructor de prueba — permite inyectar una fábrica de conexiones stub y un
     * ejecutor de UI síncrono ({@code Runnable::run}) para evitar requerir BD o toolkit JavaFX en pruebas.
     */
    SeatSyncService(ConnectionFactory connFactory, Consumer<Runnable> uiRunner) {
        this.connFactory = connFactory;
        this.uiRunner = uiRunner;
    }

    /**
     * Abre una conexión LISTEN dedicada, ejecuta {@code LISTEN seat_update},
     * e inicia el hilo de sondeo en segundo plano.
     *
     * @param callback invocado en el hilo JavaFX con el ID de asiento actualizado
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
     * Detiene el hilo de sondeo y cierra la conexión LISTEN dedicada.
     * Seguro de llamar cuando el servicio no ha sido iniciado (idempotente).
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
