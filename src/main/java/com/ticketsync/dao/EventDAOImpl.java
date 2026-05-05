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
 * Implementación JDBC de {@link EventDAO} para la tabla {@code events}.
 *
 * <p>Todo el SQL se ejecuta vía {@link PreparedStatement} usando marcadores {@code ?}.
 * Nunca se construye SQL por concatenación de cadenas, previniendo inyección SQL (OWASP A03).
 *
 * <p>Los llamadores son responsables de gestionar el ciclo de vida de {@link Connection} (abrir,
 * commit/rollback, cerrar). Esta clase nunca cierra la conexión suministrada.
 *
 * <p>La columna {@code created_by} es una FK nullable a {@code users.user_id}.
 * Cuando el campo Java {@code createdBy == 0} (no establecido), se escribe {@code NULL} en
 * la base de datos vía {@link PreparedStatement#setNull}.
 *
 * @see EventDAO
 * @see com.ticketsync.model.Event
 */
public class EventDAOImpl implements EventDAO {

    /** Crea un nuevo {@code EventDAOImpl} usando la fábrica de conexiones predeterminada. */
    public EventDAOImpl() {
    }

    // -------------------------------------------------------------------------
    // Constantes SQL
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
    // Métodos públicos de interfaz
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
     * <p>Los resultados se ordenan por {@code event_date DESC}.
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
     * <p>Devuelve solo eventos donde {@code is_active = true}, ordenados por {@code event_date DESC}.
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
     * <p>El campo {@code eventId} de {@code event} se ignora; el {@code event_id} generado por la
     * base de datos es devuelto. La columna {@code created_at} se omite del
     * INSERT para que aplique {@code DEFAULT NOW()}. Cuando {@code event.getCreatedBy() == 0},
     * se almacena {@code NULL} para la columna FK {@code created_by}.
     *
     * @throws IllegalArgumentException si {@code event} es null
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
     * <p>Actualiza {@code name}, {@code event_date}, {@code venue}, {@code description},
     * y {@code is_active} para el evento identificado por {@code event.getEventId()}.
     * Las columnas {@code created_by} y {@code created_at} son inmutables después de la creación
     * y se excluyen del UPDATE.
     *
     * @throws IllegalArgumentException si {@code event} es null o {@code event.getEventId()} es cero o negativo
     * @throws SQLException             si ninguna fila coincide con el {@code eventId} dado
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
     * @throws IllegalArgumentException si {@code eventId} es cero o negativo
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
    // Ayudantes privados
    // -------------------------------------------------------------------------

    /**
     * Mapea la fila actual de un {@link ResultSet} a un objeto {@link Event}.
     *
     * <p>La columna FK nullable {@code created_by} se mapea usando {@link ResultSet#wasNull()}
     * — cuando la columna es {@code NULL}, {@code createdBy} se deja en {@code 0}.
     * Las columnas nullable {@code venue} y {@code description} son manejadas por
     * {@link ResultSet#getString} que devuelve {@code null} directamente.
     *
     * @param rs ResultSet posicionado en la fila actual
     * @return instancia de {@link Event} poblada
     * @throws SQLException si una columna no puede ser leída
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
