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
 * Implementación JDBC de {@link ZoneDAO} para la tabla {@code zones}.
 *
 * <p>Todo el SQL se ejecuta vía {@link PreparedStatement} usando marcadores {@code ?}.
 * Nunca se construye SQL por concatenación de cadenas, previniendo inyección SQL (OWASP A03).
 *
 * <p>Los llamadores son responsables de gestionar el ciclo de vida de {@link Connection} (abrir,
 * commit/rollback, cerrar). Esta clase nunca cierra la conexión suministrada.
 *
 * @see ZoneDAO
 * @see com.ticketsync.model.Zone
 */
public class ZoneDAOImpl implements ZoneDAO {

    /** Crea un nuevo ZoneDAOImpl usando la fábrica de conexiones de producción. */
    public ZoneDAOImpl() { }

    // Constantes SQL
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
    // Métodos públicos de interfaz
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException si {@code zoneId} es cero o negativo
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
     * <p>Los resultados se ordenan por {@code zone_id ASC}.
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
     * <p>El campo {@code zoneId} de {@code zone} se ignora; el {@code zone_id} generado por la
     * base de datos es devuelto. {@code event_id}, {@code name}, y {@code price}
     * se escriben desde el {@link Zone} suministrado.
     *
     * @throws IllegalArgumentException si {@code zone} es null
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
     * <p>Actualiza {@code name} y {@code price} para la zona identificada por
     * {@code zone.getZoneId()}.
     *
     * @throws IllegalArgumentException si {@code zone} es null o {@code zone.getZoneId()} es cero o negativo
     * @throws SQLException             si ninguna fila coincide con el {@code zoneId} dado
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
     * <p>Los asientos asociados son eliminados en cascada por la restricción de BD
     * ({@code ON DELETE CASCADE} en la FK {@code seats.zone_id}).
     *
     * @throws IllegalArgumentException si {@code zoneId} es cero o negativo
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
    // Ayudantes privados
    // -------------------------------------------------------------------------

    /**
     * Mapea la fila actual de un {@link ResultSet} a un objeto {@link Zone}.
     *
     * @param rs ResultSet posicionado en la fila actual
     * @return instancia de {@link Zone} poblada
     * @throws SQLException si una columna no puede ser leída
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
