package com.ticketsync.dao;

import com.ticketsync.model.Event;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link EventDAO} for the {@code events} table.
 *
 * <p>All SQL is executed via {@link PreparedStatement} using {@code ?} placeholders.
 * No SQL is ever built by string concatenation, preventing SQL injection (OWASP A03).
 *
 * <p>Callers are responsible for managing the {@link Connection} lifecycle (open,
 * commit/rollback, close). This class never closes the supplied connection.
 *
 * <p>The {@code created_by} column is a nullable FK to {@code users.user_id}.
 * When the Java field {@code createdBy == 0} (unset), {@code NULL} is written to
 * the database via {@link PreparedStatement#setNull}.
 *
 * @see EventDAO
 * @see com.ticketsync.model.Event
 */
public class EventDAOImpl implements EventDAO {

    // -------------------------------------------------------------------------
    // SQL constants
    // -------------------------------------------------------------------------

    private static final String SQL_FIND_BY_ID =
            "SELECT event_id, name, event_date, venue, description, is_active, created_by, created_at "
            + "FROM events WHERE event_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT event_id, name, event_date, venue, description, is_active, created_by, created_at "
            + "FROM events ORDER BY event_date DESC";

    private static final String SQL_FIND_ACTIVE =
            "SELECT event_id, name, event_date, venue, description, is_active, created_by, created_at "
            + "FROM events WHERE is_active = true ORDER BY event_date DESC";

    private static final String SQL_INSERT =
            "INSERT INTO events (name, event_date, venue, description, is_active, created_by) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE events SET name = ?, event_date = ?, venue = ?, description = ?, is_active = ? "
            + "WHERE event_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM events WHERE event_id = ?";

    // -------------------------------------------------------------------------
    // Public interface methods
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code eventId} is zero or negative
     */
    @Override
    public Optional<Event> findById(Connection conn, int eventId) throws SQLException {
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive, got: " + eventId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, eventId);
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
     * <p>Results are ordered by {@code event_date DESC}.
     */
    @Override
    public List<Event> findAll(Connection conn) throws SQLException {
        List<Event> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(mapRow(rs));
            }
        }
        return events;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns only events where {@code is_active = true}, ordered by {@code event_date DESC}.
     */
    @Override
    public List<Event> findActive(Connection conn) throws SQLException {
        List<Event> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(mapRow(rs));
            }
        }
        return events;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code eventId} field of {@code event} is ignored; the database-generated
     * {@code event_id} is returned. The {@code created_at} column is omitted from the
     * INSERT so {@code DEFAULT NOW()} applies. When {@code event.getCreatedBy() == 0},
     * {@code NULL} is stored for the {@code created_by} FK column.
     *
     * @throws IllegalArgumentException if {@code event} is null
     */
    @Override
    public int insert(Connection conn, Event event) throws SQLException {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.getEventDate() == null) {
            throw new IllegalArgumentException("event.eventDate must not be null");
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.getName());
            ps.setTimestamp(2, Timestamp.valueOf(event.getEventDate()));
            ps.setString(3, event.getVenue());         // null → DB NULL (setString handles null)
            ps.setString(4, event.getDescription());   // null → DB NULL (setString handles null)
            ps.setBoolean(5, event.isActive());
            if (event.getCreatedBy() == 0) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, event.getCreatedBy());
            }
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
     * <p>Updates {@code name}, {@code event_date}, {@code venue}, {@code description},
     * and {@code is_active} for the event identified by {@code event.getEventId()}.
     * The {@code created_by} and {@code created_at} columns are immutable after creation
     * and are excluded from the UPDATE.
     *
     * @throws IllegalArgumentException if {@code event} is null or {@code event.getEventId()} is zero or negative
     * @throws SQLException             if no row matches the given {@code eventId}
     */
    @Override
    public void update(Connection conn, Event event) throws SQLException {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (event.getEventId() <= 0) {
            throw new IllegalArgumentException("event.eventId must be positive, got: " + event.getEventId());
        }
        if (event.getEventDate() == null) {
            throw new IllegalArgumentException("event.eventDate must not be null");
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, event.getName());
            ps.setTimestamp(2, Timestamp.valueOf(event.getEventDate()));
            ps.setString(3, event.getVenue());
            ps.setString(4, event.getDescription());
            ps.setBoolean(5, event.isActive());
            ps.setInt(6, event.getEventId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("No event found for update with id: " + event.getEventId());
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code eventId} is zero or negative
     */
    @Override
    public void delete(Connection conn, int eventId) throws SQLException {
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive, got: " + eventId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps the current row of a {@link ResultSet} to an {@link Event} object.
     *
     * <p>The nullable {@code created_by} FK column is mapped using {@link ResultSet#wasNull()}
     * — when the column is {@code NULL}, {@code createdBy} is left at {@code 0}.
     * The nullable {@code venue} and {@code description} columns are handled by
     * {@link ResultSet#getString} which returns {@code null} directly.
     *
     * @param rs ResultSet positioned on the current row
     * @return populated {@link Event} instance
     * @throws SQLException if a column cannot be read
     */
    private Event mapRow(ResultSet rs) throws SQLException {
        Event event = new Event();
        event.setEventId(rs.getInt("event_id"));
        event.setName(rs.getString("name"));
        Timestamp eventDate = rs.getTimestamp("event_date");
        event.setEventDate(eventDate != null ? eventDate.toLocalDateTime() : null);
        event.setVenue(rs.getString("venue"));
        event.setDescription(rs.getString("description"));
        event.setActive(rs.getBoolean("is_active"));
        int createdBy = rs.getInt("created_by");
        event.setCreatedBy(rs.wasNull() ? 0 : createdBy);
        Timestamp createdAt = rs.getTimestamp("created_at");
        event.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return event;
    }
}
