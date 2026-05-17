package com.ticketsync.service;

import com.ticketsync.dao.SeatDAO;
import com.ticketsync.dao.SeatDAOImpl;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Clase de servicio para la lógica de negocio de gestión de asientos.
 *
 * <p>Proporciona operaciones de generación, eliminación y consulta de asientos en la
 * tabla {@code seats}, delegando la persistencia a {@link SeatDAO}. Todos los
 * métodos adquieren su propia {@link Connection} vía el {@link ConnectionFactory}
 * inyectado y la liberan vía try-with-resources.
 *
 * <p>Todas las operaciones mutantes requieren una sesión ADMIN activa en
 * {@link SessionContext}. Se lanza una {@link SecurityException} si el
 * llamador no tiene el rol {@code ADMIN}.
 */
public class SeatService {

    private static final Logger LOGGER = LogManager.getLogger(SeatService.class);

    private final SeatDAO seatDAO;
    private final AuditService auditService;
    private final ConnectionFactory connFactory;

    /**
     * Constructor de producción — crea una instancia activa de {@link SeatDAOImpl}
     * y usa {@link DatabaseConfig#getConnection()} para la adquisición de conexiones.
     */
    public SeatService() {
        this(new SeatDAOImpl(), new AuditService(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección completa en pruebas unitarias (sin BD requerida).
     *
     * @param seatDAO     el stub DAO; no debe ser {@code null}
     * @param connFactory el proveedor de conexiones stub; no debe ser {@code null}
     */
    SeatService(SeatDAO seatDAO, ConnectionFactory connFactory) {
        this(seatDAO, AuditService.noop(), connFactory);
    }

    /**
     * Constructor de paquete con costura de auditoría inyectable.
     */
    SeatService(SeatDAO seatDAO, AuditService auditService, ConnectionFactory connFactory) {
        this.seatDAO = seatDAO;
        this.auditService = auditService;
        this.connFactory = connFactory;
    }

    // -----------------------------------------------------------------------
    // Ayudantes privados
    // -----------------------------------------------------------------------

    private void requireAdminRole() {
        if (!SessionContext.hasRole("ADMIN")) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
    }

    // -----------------------------------------------------------------------
    // API Pública
    // -----------------------------------------------------------------------

    /**
     * Genera un rango de asientos para la zona especificada en una sola
     * transacción de base de datos. Si algún insert de asiento falla (p.ej.,
     * violación de restricción única duplicada), toda la transacción se revierte.
     *
     * @param zoneId     la zona a la que agregar asientos; debe ser positivo
     * @param rowNumber  la etiqueta de fila (p.ej., "A"); no debe estar en blanco
     * @param fromSeat   el primer número de asiento en el rango; debe ser &ge; 1
     * @param toSeat     el último número de asiento en el rango; debe ser &ge; fromSeat
     * @throws IllegalArgumentException si alguno parámetro es inválido
     * @throws SecurityException        si el llamador no es ADMIN
     * @throws SQLException             si ocurre un error de base de datos (transacción revertida)
     */
    public void generateSeats(int zoneId, String rowNumber, int fromSeat, int toSeat) throws SQLException {
        requireAdminRole();
        if (zoneId <= 0) throw new IllegalArgumentException("zoneId must be positive");
        if (rowNumber == null || rowNumber.isBlank()) throw new IllegalArgumentException("rowNumber must not be blank");
        if (fromSeat < 1) throw new IllegalArgumentException("fromSeat must be >= 1");
        if (toSeat < fromSeat) throw new IllegalArgumentException("toSeat must be >= fromSeat");
        if (toSeat - fromSeat + 1 > 1000) throw new IllegalArgumentException("seat range must not exceed 1000 seats per generation");

        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        int count = toSeat - fromSeat + 1;

        try (Connection conn = connFactory.get()) {
            conn.setAutoCommit(false);
            try {
                for (int i = fromSeat; i <= toSeat; i++) {
                    Seat seat = new Seat();
                    seat.setZoneId(zoneId);
                    seat.setRowNumber(rowNumber.strip());
                    seat.setSeatNumber(String.valueOf(i));
                    seat.setStatus(SeatStatus.AVAILABLE);
                    seatDAO.insert(conn, seat);
                }
                conn.commit();
                LOGGER.info("Admin '{}' generated {} seats in zone {} row '{}'",
                        adminUsername, count, zoneId, rowNumber);
                auditService.logSeatsGenerated(zoneId, rowNumber.strip(), fromSeat, toSeat);
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.error("Failed to generate seats in zone {} — rolled back", zoneId, e);
                throw e;
            }
        }
    }

    /**
     * Elimina un lote de asientos dentro de una transacción gestionada. Privado de paquete
     * para poder ser llamado desde dentro de una transacción abierta por el llamador.
     *
     * @param conn    conexión activa con autoCommit desactivado
     * @param seatIds lista de IDs de asientos a eliminar
     * @throws SQLException si alguna eliminación falla
     */
    void deleteSeatsBatch(Connection conn, List<Integer> seatIds) throws SQLException {
        for (int seatId : seatIds) {
            seatDAO.delete(conn, seatId);
        }
    }

    /**
     * Elimina los asientos especificados en una sola transacción de base de datos.
     * En cualquier fallo la transacción se revierte.
     *
     * @param seatIds lista de IDs de asientos a eliminar; no debe estar vacía
     * @throws SecurityException si el llamador no es ADMIN
     * @throws SQLException      si ocurre un error de base de datos (transacción revertida)
     */
    public void deleteSeatsTransaction(List<Integer> seatIds) throws SQLException {
        requireAdminRole();
        if (seatIds == null || seatIds.isEmpty()) return;

        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");

        try (Connection conn = connFactory.get()) {
            conn.setAutoCommit(false);
            try {
                deleteSeatsBatch(conn, seatIds);
                conn.commit();
                LOGGER.info("Admin '{}' deleted {} seats", adminUsername, seatIds.size());
                auditService.logSeatsDeleted(List.copyOf(seatIds));
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.error("Failed to delete seats — rolled back", e);
                throw e;
            }
        }
    }

    /**
     * Devuelve todos los asientos para la zona especificada ordenados por row_number ASC,
     * seat_number ASC.
     *
     * @param zoneId la zona a consultar; debe ser positivo
     * @return lista de asientos; vacía si no existen
     * @throws SQLException si ocurre un error de base de datos
     */
    public List<Seat> getSeatsByZone(int zoneId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return seatDAO.findByZoneId(conn, zoneId);
        }
    }

    /**
     * Devuelve todos los asientos para un evento dado en todas las zonas, ordenados por zone_id, fila, asiento.
     * Usa aislamiento READ_COMMITTED (solo lectura, sin bloqueo necesario).
     *
     * @param eventId el identificador del evento; debe ser positivo
     * @return lista de todos los asientos para el evento; nunca {@code null}
     * @throws java.sql.SQLException si ocurre un error de base de datos
     */
    public List<Seat> getSeatsForEvent(int eventId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return seatDAO.findByEventId(conn, eventId);
        }
    }

    /**
     * Devuelve un solo asiento por clave primaria.
     *
     * @param seatId el asiento a buscar; debe ser positivo
     * @return el asiento cuando se encuentra, en caso contrario {@link Optional#empty()}
     * @throws SQLException si ocurre un error de base de datos
     */
    public Optional<Seat> getSeatById(int seatId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return seatDAO.findById(conn, seatId);
        }
    }

    /**
     * Actualiza el estado de los asientos especificados en una sola transacción de base de datos.
     * Solo AVAILABLE y DISABLED son estados de destino válidos para operaciones administrativas.
     * En cualquier fallo la transacción se revierte.
     *
     * @param seatIds      lista de IDs de asientos a actualizar; null o lista vacía es una operación sin efecto
     * @param targetStatus el nuevo estado deseado; no debe ser null, SOLD ni RESERVED
     * @throws IllegalArgumentException si targetStatus es null, SOLD o RESERVED
     * @throws SecurityException        si el llamador no es ADMIN
     * @throws SQLException             si ocurre un error de base de datos (transacción revertida)
     */
    public void updateSeatStatus(List<Integer> seatIds, SeatStatus targetStatus) throws SQLException {
        requireAdminRole();
        if (seatIds == null || seatIds.isEmpty()) return;
        if (targetStatus == null)
            throw new IllegalArgumentException("targetStatus must not be null");
        if (targetStatus == SeatStatus.SOLD)
            throw new IllegalArgumentException("Cannot set seat status to SOLD via admin — use TransactionService");
        if (targetStatus == SeatStatus.RESERVED)
            throw new IllegalArgumentException("RESERVED status is not used in admin workflows");

        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");

        try (Connection conn = connFactory.get()) {
            conn.setAutoCommit(false);
            try {
                seatDAO.updateStatus(conn, seatIds, targetStatus, null);
                conn.commit();
                LOGGER.info("Admin '{}' updated {} seats to status {}", adminUsername, seatIds.size(), targetStatus);
                auditService.logSeatStatusUpdated(List.copyOf(seatIds), targetStatus);
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.error("Failed to update seat status to {} — rolled back", targetStatus, e);
                throw e;
            }
        }
    }

    /**
     * Reserva atómicamente los asientos especificados para el vendor actualmente autenticado.
     *
     * <p>Solo los asientos {@code AVAILABLE} (o con reserva expirada) son elegibles.
     * Los asientos ya reservados por otro vendor son omitidos. La reserva expira en
     * {@code ttlSeconds} segundos.
     *
     * @param seatIds    IDs de asientos a reservar; no debe ser {@code null} ni vacía
     * @param ttlSeconds segundos hasta la expiración de la reserva; debe ser positivo
     * @return lista de IDs de asientos que fueron efectivamente reservados
     * @throws IllegalStateException si no hay usuario autenticado
     * @throws SQLException          si ocurre un error de base de datos
     */
    public List<Integer> reserveSeats(List<Integer> seatIds, int ttlSeconds) throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) return List.of();
        String reservedBy = SessionContext.getCurrentUser()
                .map(u -> String.valueOf(u.getUserId()))
                .orElseThrow(() -> new IllegalStateException("No user logged in — cannot reserve seats"));
        return reserveSeatsAs(seatIds, ttlSeconds, reservedBy);
    }

    /**
     * Reserva atómicamente los asientos especificados para el vendor identificado por {@code reservedBy}.
     *
     * <p>Equivalente a {@link #reserveSeats} pero acepta el ID del vendor explícitamente,
     * permitiendo la llamada desde hilos que no tienen {@link SessionContext} configurado.
     *
     * @param seatIds    IDs de asientos a reservar; no debe ser {@code null} ni vacía
     * @param ttlSeconds segundos hasta la expiración de la reserva; debe ser positivo
     * @param reservedBy identificador del vendor; no debe ser {@code null}
     * @return lista de IDs de asientos que fueron efectivamente reservados
     * @throws SQLException si ocurre un error de base de datos
     */
    public List<Integer> reserveSeatsAs(List<Integer> seatIds, int ttlSeconds, String reservedBy)
            throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) return List.of();
        try (Connection conn = connFactory.get()) {
            List<Integer> reserved = seatDAO.reserveSeats(conn, seatIds, reservedBy, ttlSeconds);
            if (!reserved.isEmpty()) {
                LOGGER.debug("User {} reserved {} seat(s)", reservedBy, reserved.size());
            }
            return reserved;
        }
    }

    /**
     * Libera las reservas activas que pertenezcan al vendor actualmente autenticado
     * para los asientos dados.
     *
     * <p>Asientos no reservados o reservados por otro vendor son ignorados silenciosamente.
     * Si no hay usuario en sesión no se realiza ninguna acción.
     *
     * @param seatIds IDs de asientos a liberar; si es {@code null} o vacía no hace nada
     * @throws SQLException si ocurre un error de base de datos
     */
    public void releaseSeats(List<Integer> seatIds) throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) return;
        String reservedBy = SessionContext.getCurrentUser()
                .map(u -> String.valueOf(u.getUserId()))
                .orElse(null);
        if (reservedBy == null) return;
        releaseSeatsAs(seatIds, reservedBy);
    }

    /**
     * Libera las reservas activas identificadas por {@code reservedBy} para los asientos dados.
     *
     * <p>Equivalente a {@link #releaseSeats} pero acepta el ID del vendor explícitamente,
     * permitiendo la llamada desde hilos sin {@link SessionContext} configurado.
     *
     * @param seatIds    IDs de asientos a liberar; si es {@code null} o vacía no hace nada
     * @param reservedBy identificador del vendor que posee las reservas; no debe ser {@code null}
     * @throws SQLException si ocurre un error de base de datos
     */
    public void releaseSeatsAs(List<Integer> seatIds, String reservedBy) throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) return;
        try (Connection conn = connFactory.get()) {
            seatDAO.releaseReservation(conn, seatIds, reservedBy);
            LOGGER.debug("User {} released {} seat reservation(s)", reservedBy, seatIds.size());
        }
    }
}
