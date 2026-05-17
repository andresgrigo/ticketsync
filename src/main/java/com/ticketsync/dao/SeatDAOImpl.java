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
import java.util.stream.Collectors;

/**
 * Implementación JDBC de {@link SeatDAO} para la tabla {@code seats}.
 *
 * <p>Todo el SQL se ejecuta vía {@link PreparedStatement} usando marcadores {@code ?}.
 * Nunca se construye SQL por concatenación de cadenas, previniendo inyección SQL (OWASP A03).
 *
 * <p>Los llamadores son responsables de gestionar el ciclo de vida de {@link Connection} (abrir,
 * commit/rollback, cerrar). Esta clase nunca cierra la conexión suministrada.
 *
 * <p>El método {@link #selectForUpdate} usa el bloqueo a nivel de fila de PostgreSQL
 * ({@code SELECT … FOR UPDATE}) dentro de una transacción {@code SERIALIZABLE}
 * para garantizar cero sobreventas en compras de asientos concurrentes.
 *
 * @see SeatDAO
 * @see com.ticketsync.model.Seat
 * @see SeatStatus
 */
public class SeatDAOImpl implements SeatDAO {

    /** Crea un nuevo {@code SeatDAOImpl} usando la fábrica de conexiones predeterminada. */
    public SeatDAOImpl() {
    }

    // -------------------------------------------------------------------------
    // Constantes SQL
    // -------------------------------------------------------------------------

    private static final String SQL_FIND_BY_ID =
            "SELECT seat_id, zone_id, row_number, seat_number, status, sale_id, reserved_by"
            + " FROM seats WHERE seat_id = ?";

    private static final String SQL_FIND_BY_ZONE_ID =
            "SELECT seat_id, zone_id, row_number, seat_number, status, sale_id, reserved_by "
            + "FROM seats WHERE zone_id = ? ORDER BY row_number ASC, seat_number ASC";

    private static final String SQL_FIND_BY_EVENT_ID =
            "SELECT s.seat_id, s.zone_id, s.row_number, s.seat_number,"
            + " CASE WHEN s.status = 'RESERVED' AND s.reserved_until IS NOT NULL AND s.reserved_until < NOW()"
            + "      THEN 'AVAILABLE' ELSE s.status END AS status,"
            + " s.sale_id, s.reserved_by"
            + " FROM seats s JOIN zones z ON s.zone_id = z.zone_id"
            + " WHERE z.event_id = ? ORDER BY s.zone_id ASC, s.row_number ASC, s.seat_number ASC";

    private static final String SQL_SELECT_FOR_UPDATE =
            "SELECT seat_id, zone_id, row_number, seat_number, status, sale_id, reserved_by "
            + "FROM seats WHERE seat_id = ANY(?) FOR UPDATE";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE seats SET status = ?, sale_id = ? WHERE seat_id = ANY(?)";

    private static final String SQL_RESERVE_SEATS =
            "UPDATE seats SET status = 'RESERVED', reserved_by = ?, reserved_until = NOW() + (? * INTERVAL '1 second')"
            + " WHERE seat_id = ANY(?)"
            + " AND (status = 'AVAILABLE' OR (status = 'RESERVED' AND reserved_until < NOW()))"
            + " RETURNING seat_id";

    private static final String SQL_RELEASE_RESERVATION =
            "UPDATE seats SET status = 'AVAILABLE', reserved_by = NULL, reserved_until = NULL"
            + " WHERE seat_id = ANY(?) AND reserved_by = ? AND status = 'RESERVED'";

    private static final String SQL_INSERT =
            "INSERT INTO seats (zone_id, row_number, seat_number, status) VALUES (?, ?, ?, ?)";

    private static final String SQL_DELETE =
            "DELETE FROM seats WHERE seat_id = ?";

    // -------------------------------------------------------------------------
    // Métodos públicos de interfaz
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException si {@code seatId} es cero o negativo
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
     * <p>Los resultados se ordenan por {@code row_number ASC, seat_number ASC}.
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
     * <p>Realiza un JOIN en {@code zones} para recuperar todos los asientos de todas las zonas del
     * evento especificado. Los resultados se ordenan por {@code zone_id ASC, row_number ASC, seat_number ASC}.
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
     * <p>Usa la sintaxis {@code ANY(?)} de PostgreSQL con un {@link Array} JDBC para bloquear
     * todos los asientos especificados atómicamente. Los bloqueos exclusivos a nivel de fila se mantienen hasta
     * que la transacción que los contiene se confirma o revierte.
     *
     * @throws IllegalArgumentException si {@code seatIds} es null o vacía
     * @throws IllegalStateException    si el nivel de aislamiento de la conexión no es
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
     * <p>Cuando {@code status} es {@link SeatStatus#SOLD}, {@code saleId} se escribe como el
     * valor entero suministrado. Para todos los demás estados ({@code AVAILABLE}, {@code DISABLED},
     * {@code RESERVED}), {@code sale_id} se establece en {@code NULL}.
     *
     * @throws IllegalArgumentException si {@code seatIds} es null o vacía, {@code status}
     *                                  es null, o {@code status == SOLD} y {@code saleId == null}
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
     * <p>El campo {@code seatId} de {@code seat} se ignora; el {@code seat_id} generado por la
     * base de datos es devuelto. La columna {@code sale_id} se omite intencionalmente
     * del INSERT (por defecto {@code NULL}) porque los nuevos asientos nunca se venden en la creación.
     *
     * @throws IllegalArgumentException si {@code seat} es null
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
     * @throws IllegalArgumentException si {@code seatId} es cero o negativo
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
    // Ayudantes privados
    // -------------------------------------------------------------------------

    @Override
    public List<Integer> reserveSeats(Connection conn, List<Integer> seatIds, String reservedBy, int ttlSeconds)
            throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be null or empty");
        }
        if (reservedBy == null) {
            throw new IllegalArgumentException("reservedBy must not be null");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        Array seatArray = conn.createArrayOf("INTEGER", seatIds.toArray(new Integer[0]));
        List<Integer> reserved = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_RESERVE_SEATS)) {
            ps.setString(1, reservedBy);
            ps.setInt(2, ttlSeconds);
            ps.setArray(3, seatArray);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reserved.add(rs.getInt(1));
                }
            }
        } finally {
            seatArray.free();
        }
        return reserved;
    }

    @Override
    public void releaseReservation(Connection conn, List<Integer> seatIds, String reservedBy)
            throws SQLException {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be null or empty");
        }
        if (reservedBy == null) {
            throw new IllegalArgumentException("reservedBy must not be null");
        }
        Array seatArray = conn.createArrayOf("INTEGER", seatIds.toArray(new Integer[0]));
        try (PreparedStatement ps = conn.prepareStatement(SQL_RELEASE_RESERVATION)) {
            ps.setArray(1, seatArray);
            ps.setString(2, reservedBy);
            ps.executeUpdate();
        } finally {
            seatArray.free();
        }
    }

    /**
     * Mapea la fila actual de un {@link ResultSet} a un objeto {@link Seat}.
     *
     * <p>La columna nullable {@code sale_id} se maneja vía {@link ResultSet#wasNull()}
     * — cuando la columna es {@code NULL}, {@code saleId} se establece en {@code null}.
     *
     * @param rs ResultSet posicionado en la fila actual
     * @return instancia de {@link Seat} poblada
     * @throws SQLException si una columna no puede ser leída
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
        seat.setReservedBy(rs.getString("reserved_by"));
        return seat;
    }
}
