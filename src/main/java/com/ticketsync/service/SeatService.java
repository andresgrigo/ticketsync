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
 * Service class for seat management business logic.
 *
 * <p>Provides seat generation, deletion, and query operations on the
 * {@code seats} table, delegating persistence to {@link SeatDAO}. All
 * methods acquire their own {@link Connection} via the injected
 * {@link ConnectionFactory} and release it via try-with-resources.
 *
 * <p>All mutating operations require an active ADMIN session in
 * {@link SessionContext}. A {@link SecurityException} is thrown if the
 * caller does not have the {@code ADMIN} role.
 */
public class SeatService {

    private static final Logger LOGGER = LogManager.getLogger(SeatService.class);

    private final SeatDAO seatDAO;
    private final AuditService auditService;
    private final ConnectionFactory connFactory;

    /**
     * Production constructor — creates a live {@link SeatDAOImpl} instance
     * and uses {@link DatabaseConfig#getConnection()} for connection
     * acquisition.
     */
    public SeatService() {
        this(new SeatDAOImpl(), new AuditService(), DatabaseConfig::getConnection);
    }

    /**
     * Package-private constructor for full unit-test injection (no DB required).
     *
     * @param seatDAO     the DAO stub; must not be {@code null}
     * @param connFactory the connection provider stub; must not be {@code null}
     */
    SeatService(SeatDAO seatDAO, ConnectionFactory connFactory) {
        this(seatDAO, AuditService.noop(), connFactory);
    }

    /**
     * Package-private constructor with injectable audit seam.
     */
    SeatService(SeatDAO seatDAO, AuditService auditService, ConnectionFactory connFactory) {
        this.seatDAO = seatDAO;
        this.auditService = auditService;
        this.connFactory = connFactory;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void requireAdminRole() {
        if (!SessionContext.hasRole("ADMIN")) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generates a range of seats for the specified zone in a single
     * database transaction. If any seat insertion fails (e.g., duplicate
     * unique-constraint violation), the entire transaction is rolled back.
     *
     * @param zoneId     the zone to add seats to; must be positive
     * @param rowNumber  the row label (e.g., "A"); must not be blank
     * @param fromSeat   the first seat number in the range; must be &ge; 1
     * @param toSeat     the last seat number in the range; must be &ge; fromSeat
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws SecurityException        if the caller is not ADMIN
     * @throws SQLException             if a database error occurs (transaction rolled back)
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
     * Deletes a batch of seats inside a managed transaction. Package-private
     * so it can be called from within a transaction opened by the caller.
     *
     * @param conn    active connection with autoCommit disabled
     * @param seatIds list of seat IDs to delete
     * @throws SQLException if any deletion fails
     */
    void deleteSeatsBatch(Connection conn, List<Integer> seatIds) throws SQLException {
        for (int seatId : seatIds) {
            seatDAO.delete(conn, seatId);
        }
    }

    /**
     * Deletes the specified seats in a single database transaction.
     * On any failure the transaction is rolled back.
     *
     * @param seatIds list of seat IDs to delete; must not be empty
     * @throws SecurityException if the caller is not ADMIN
     * @throws SQLException      if a database error occurs (transaction rolled back)
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
     * Returns all seats for the specified zone ordered by row_number ASC,
     * seat_number ASC.
     *
     * @param zoneId the zone to query; must be positive
     * @return list of seats; empty if none exist
     * @throws SQLException if a database error occurs
     */
    public List<Seat> getSeatsByZone(int zoneId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return seatDAO.findByZoneId(conn, zoneId);
        }
    }

    /**
     * Returns all seats for a given event across all zones, ordered by zone_id, row, seat.
     * Uses READ_COMMITTED isolation (read-only, no locking needed).
     */
    public List<Seat> getSeatsForEvent(int eventId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return seatDAO.findByEventId(conn, eventId);
        }
    }

    /**
     * Returns a single seat by primary key.
     *
     * @param seatId the seat to look up; must be positive
     * @return the seat when found, otherwise {@link Optional#empty()}
     * @throws SQLException if a database error occurs
     */
    public Optional<Seat> getSeatById(int seatId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return seatDAO.findById(conn, seatId);
        }
    }

    /**
     * Updates the status of the specified seats in a single database transaction.
     * Only AVAILABLE and DISABLED are valid target statuses for admin operations.
     * On any failure the transaction is rolled back.
     *
     * @param seatIds      list of seat IDs to update; null or empty list is a no-op
     * @param targetStatus the desired new status; must not be null, SOLD, or RESERVED
     * @throws IllegalArgumentException if targetStatus is null, SOLD, or RESERVED
     * @throws SecurityException        if the caller is not ADMIN
     * @throws SQLException             if a database error occurs (transaction rolled back)
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
}
