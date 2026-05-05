package com.ticketsync.dao;

import com.ticketsync.model.Zone;
 
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link ZoneDAO} for the {@code zones} table.
 *
 * <p>All SQL is executed via {@link PreparedStatement} using {@code ?} placeholders.
 * No SQL is ever built by string concatenation, preventing SQL injection (OWASP A03).
 *
 * <p>Callers are responsible for managing the {@link Connection} lifecycle (open,
 * commit/rollback, close). This class never closes the supplied connection.
 *
 * @see ZoneDAO
 * @see com.ticketsync.model.Zone
 */
public class ZoneDAOImpl implements ZoneDAO {

    /** Creates a new ZoneDAOImpl using the production connection factory. */
    public ZoneDAOImpl() { }

    // SQL constants
    // -------------------------------------------------------------------------

    private static final String SQL_FIND_BY_ID =
            "SELECT zone_id, event_id, name, price FROM zones WHERE zone_id = ?";

    private static final String SQL_FIND_BY_EVENT_ID =
            "SELECT zone_id, event_id, name, price FROM zones WHERE event_id = ? ORDER BY zone_id ASC";

    private static final String SQL_INSERT =
            "INSERT INTO zones (event_id, name, price) VALUES (?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE zones SET name = ?, price = ? WHERE zone_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM zones WHERE zone_id = ?";

    // -------------------------------------------------------------------------
    // Public interface methods
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code zoneId} is zero or negative
     */
    @Override
    public Optional<Zone> findById(Connection conn, int zoneId) throws SQLException {
        if (zoneId <= 0) {
            throw new IllegalArgumentException("zoneId must be positive, got: " + zoneId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, zoneId);
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
     * <p>Results are ordered by {@code zone_id ASC}.
     */
    @Override
    public List<Zone> findByEventId(Connection conn, int eventId) throws SQLException {
        List<Zone> zones = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_EVENT_ID)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    zones.add(mapRow(rs));
                }
            }
        }
        return zones;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code zoneId} field of {@code zone} is ignored; the database-generated
     * {@code zone_id} is returned. {@code event_id}, {@code name}, and {@code price}
     * are written from the supplied {@link Zone}.
     *
     * @throws IllegalArgumentException if {@code zone} is null
     */
    @Override
    public int insert(Connection conn, Zone zone) throws SQLException {
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, zone.getEventId());
            ps.setString(2, zone.getName());
            ps.setBigDecimal(3, zone.getPrice());
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
     * <p>Updates {@code name} and {@code price} for the zone identified by
     * {@code zone.getZoneId()}.
     *
     * @throws IllegalArgumentException if {@code zone} is null or {@code zone.getZoneId()} is zero or negative
     * @throws SQLException             if no row matches the given {@code zoneId}
     */
    @Override
    public void update(Connection conn, Zone zone) throws SQLException {
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }
        if (zone.getZoneId() <= 0) {
            throw new IllegalArgumentException("zone.zoneId must be positive, got: " + zone.getZoneId());
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, zone.getName());
            ps.setBigDecimal(2, zone.getPrice());
            ps.setInt(3, zone.getZoneId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("No zone found for update with id: " + zone.getZoneId());
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Associated seats are cascade-deleted by the DB constraint
     * ({@code ON DELETE CASCADE} on the {@code seats.zone_id} FK).
     *
     * @throws IllegalArgumentException if {@code zoneId} is zero or negative
     */
    @Override
    public void delete(Connection conn, int zoneId) throws SQLException {
        if (zoneId <= 0) {
            throw new IllegalArgumentException("zoneId must be positive, got: " + zoneId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, zoneId);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps the current row of a {@link ResultSet} to a {@link Zone} object.
     *
     * @param rs ResultSet positioned on the current row
     * @return populated {@link Zone} instance
     * @throws SQLException if a column cannot be read
     */
    private Zone mapRow(ResultSet rs) throws SQLException {
        Zone zone = new Zone();
        zone.setZoneId(rs.getInt("zone_id"));
        zone.setEventId(rs.getInt("event_id"));
        zone.setName(rs.getString("name"));
        zone.setPrice(rs.getBigDecimal("price"));
        return zone;
    }
}
