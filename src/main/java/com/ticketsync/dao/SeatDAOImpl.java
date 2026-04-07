package com.ticketsync.dao;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link SeatDAO} for the {@code seats} table.
 *
 * <p>All SQL is executed via {@link PreparedStatement} using {@code ?} placeholders.
 * No SQL is ever built by string concatenation, preventing SQL injection (OWASP A03).
 *
 * <p>Callers are responsible for managing the {@link Connection} lifecycle (open,
 * commit/rollback, close). This class never closes the supplied connection.
 *
 * <p>The {@link #selectForUpdate} method uses PostgreSQL row-level locking
 * ({@code SELECT … FOR UPDATE}) inside a {@code SERIALIZABLE} transaction
 * to guarantee zero-oversell for concurrent seat purchases.
 *
 * @see SeatDAO
 * @see com.ticketsync.model.Seat
 * @see SeatStatus
 */
public class SeatDAOImpl implements SeatDAO {

    // -------------------------------------------------------------------------
    // SQL constants
    // -------------------------------------------------------------------------

    private static final String SQL_FIND_BY_ID =
            "SELECT seat_id, zone_id, row_number, seat_number, status, sale_id FROM seats WHERE seat_id = ?";

    private static final String SQL_FIND_BY_ZONE_ID =
            "SELECT seat_id, zone_id, row_number, seat_number, status, sale_id "
            + "FROM seats WHERE zone_id = ? ORDER BY row_number ASC, seat_number ASC";

    private static final String SQL_FIND_BY_EVENT_ID =
            "SELECT s.seat_id, s.zone_id, s.row_number, s.seat_number, s.status, s.sale_id "
            + "FROM seats s JOIN zones z ON s.zone_id = z.zone_id "
            + "WHERE z.event_id = ? ORDER BY s.zone_id ASC, s.row_number ASC, s.seat_number ASC";

    private static final String SQL_SELECT_FOR_UPDATE =
            "SELECT seat_id, zone_id, row_number, seat_number, status, sale_id "
            + "FROM seats WHERE seat_id = ANY(?) FOR UPDATE";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE seats SET status = ?, sale_id = ? WHERE seat_id = ANY(?)";

    private static final String SQL_INSERT =
            "INSERT INTO seats (zone_id, row_number, seat_number, status) VALUES (?, ?, ?, ?)";

    private static final String SQL_DELETE =
            "DELETE FROM seats WHERE seat_id = ?";

    // -------------------------------------------------------------------------
    // Public interface methods
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code seatId} is zero or negative
     */
    @Override
    public Optional<Seat> findById(Connection conn, int seatId) throws SQLException {
        if (seatId <= 0) {
            throw new IllegalArgumentException("seatId must be positive, got: " + seatId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, seatId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Results are ordered by {@code row_number ASC, seat_number ASC}.
     */
    @Override
    public List<Seat> findByZoneId(Connection conn, int zoneId) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ZONE_ID)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(mapRow(rs));
                }
            }
        }
        return seats;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs a JOIN on {@code zones} to retrieve all seats across all zones of the
     * specified event. Results are ordered by {@code zone_id ASC, row_number ASC, seat_number ASC}.
     */
    @Override
    public List<Seat> findByEventId(Connection conn, int eventId) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_EVENT_ID)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(mapRow(rs));
                }
            }
        }
        return seats;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses PostgreSQL {@code ANY(?)} syntax with a JDBC {@link Array} to lock
     * all specified seats atomically. The exclusive row-level locks are held until
     * the enclosing transaction commits or rolls back.
     *
     * @throws IllegalArgumentException if {@code seatIds} is null or empty
     * @throws IllegalStateException    if the connection isolation level is not
     *                                  {@link Connection#TRANSACTION_SERIALIZABLE}
     */
    @Override
    public List<Seat> selectForUpdate(Connection conn, List<Integer> seatIds) throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be null or empty");
        }
        if (conn.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw new IllegalStateException(
                    "selectForUpdate requires SERIALIZABLE isolation, got: "
                    + conn.getTransactionIsolation());
        }
        Array seatArray = conn.createArrayOf("INTEGER", seatIds.toArray(new Integer[0]));
        List<Seat> seats = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_SELECT_FOR_UPDATE)) {
            ps.setArray(1, seatArray);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(mapRow(rs));
                }
            }
        } finally {
            seatArray.free();
        }
        return seats;
    }

    /**
     * {@inheritDoc}
     *
     * <p>When {@code status} is {@link SeatStatus#SOLD}, {@code saleId} is written as the
     * supplied integer value. For all other statuses ({@code AVAILABLE}, {@code DISABLED},
     * {@code RESERVED}), {@code sale_id} is set to {@code NULL}.
     *
     * @throws IllegalArgumentException if {@code seatIds} is null or empty, {@code status}
     *                                  is null, or {@code status == SOLD} and {@code saleId == null}
     */
    @Override
    public void updateStatus(Connection conn, List<Integer> seatIds, SeatStatus status, Integer saleId)
            throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be null or empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == SeatStatus.SOLD && saleId == null) {
            throw new IllegalArgumentException("saleId must not be null when status is SOLD");
        }
        Array seatArray = conn.createArrayOf("INTEGER", seatIds.toArray(new Integer[0]));
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {
            ps.setString(1, status.name());
            if (status == SeatStatus.SOLD) {
                ps.setInt(2, saleId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setArray(3, seatArray);
            ps.executeUpdate();
        } finally {
            seatArray.free();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code seatId} field of {@code seat} is ignored; the database-generated
     * {@code seat_id} is returned. The {@code sale_id} column is intentionally omitted
     * from the INSERT (defaults to {@code NULL}) because new seats are never sold on creation.
     *
     * @throws IllegalArgumentException if {@code seat} is null
     */
    @Override
    public int insert(Connection conn, Seat seat) throws SQLException {
        if (seat == null) {
            throw new IllegalArgumentException("seat must not be null");
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, seat.getZoneId());
            ps.setString(2, seat.getRowNumber());
            ps.setString(3, seat.getSeatNumber());
            ps.setString(4, seat.getStatus() != null ? seat.getStatus().name() : SeatStatus.AVAILABLE.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Insert succeeded but no generated key was returned");
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code seatId} is zero or negative
     */
    @Override
    public void delete(Connection conn, int seatId) throws SQLException {
        if (seatId <= 0) {
            throw new IllegalArgumentException("seatId must be positive, got: " + seatId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, seatId);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps the current row of a {@link ResultSet} to a {@link Seat} object.
     *
     * <p>The nullable {@code sale_id} column is handled via {@link ResultSet#wasNull()}
     * — when the column is {@code NULL}, {@code saleId} is set to {@code null}.
     *
     * @param rs ResultSet positioned on the current row
     * @return populated {@link Seat} instance
     * @throws SQLException if a column cannot be read
     */
    private Seat mapRow(ResultSet rs) throws SQLException {
        Seat seat = new Seat();
        seat.setSeatId(rs.getInt("seat_id"));
        seat.setZoneId(rs.getInt("zone_id"));
        seat.setRowNumber(rs.getString("row_number"));
        seat.setSeatNumber(rs.getString("seat_number"));
        seat.setStatus(SeatStatus.valueOf(rs.getString("status")));
        int saleIdVal = rs.getInt("sale_id");
        seat.setSaleId(rs.wasNull() ? null : saleIdVal);
        return seat;
    }
}
