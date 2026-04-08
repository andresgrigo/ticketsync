package com.ticketsync.service;

import com.ticketsync.dao.ZoneDAO;
import com.ticketsync.dao.ZoneDAOImpl;
import com.ticketsync.model.User;
import com.ticketsync.model.Zone;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Service class for zone management business logic.
 *
 * <p>Provides create, update, delete, and query operations on the {@code zones}
 * table, delegating persistence to {@link ZoneDAO}. All methods acquire their own
 * {@link Connection} via the injected {@link ConnectionFactory} and release it via
 * try-with-resources.
 *
 * <p>All mutating operations require an active ADMIN session in {@link SessionContext}.
 * A {@link SecurityException} is thrown if the caller does not have the {@code ADMIN} role.
 *
 * <p>All mutating operations log an audit trail entry at INFO level.
 * {@link SQLException} from DAO calls is caught, logged at ERROR level, and re-thrown.
 */
public class ZoneService {

    private static final Logger LOGGER = LogManager.getLogger(ZoneService.class);

    private static final String SQL_COUNT_SEATS = "SELECT COUNT(*) FROM seats WHERE zone_id = ?";

    private final ZoneDAO zoneDAO;
    private final ConnectionFactory connFactory;

    /**
     * Production constructor — creates a live {@link ZoneDAOImpl} instance and
     * uses {@link DatabaseConfig#getConnection()} for connection acquisition.
     */
    public ZoneService() {
        this.zoneDAO = new ZoneDAOImpl();
        this.connFactory = DatabaseConfig::getConnection;
    }

    /**
     * Package-private constructor for full unit-test injection (no DB required).
     *
     * @param zoneDAO     the DAO stub; must not be {@code null}
     * @param connFactory the connection provider stub; must not be {@code null}
     */
    ZoneService(ZoneDAO zoneDAO, ConnectionFactory connFactory) {
        this.zoneDAO = zoneDAO;
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
     * Creates a new zone for the specified event.
     *
     * @param eventId the event to associate the zone with; must be positive
     * @param name    the zone name; must not be blank
     * @param price   the ticket price; must be &gt; 0
     * @return the generated {@code zone_id}
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if name is blank or price is not positive
     * @throws SQLException             if a database access error occurs
     */
    public int createZone(int eventId, String name, BigDecimal price) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        Zone zone = new Zone();
        zone.setEventId(eventId);
        zone.setName(name.strip());
        zone.setPrice(price);
        try (Connection conn = connFactory.get()) {
            int newId = zoneDAO.insert(conn, zone);
            LOGGER.info("Admin '{}' created zone '{}'", adminUsername, name);
            return newId;
        } catch (SQLException e) {
            LOGGER.error("Failed to create zone '{}'", name, e);
            throw e;
        }
    }

    /**
     * Updates an existing zone's name and price.
     *
     * @param zone the zone with updated fields; {@code zoneId} must be positive,
     *             name must not be blank, price must be &gt; 0
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if validation fails
     * @throws SQLException             if a database access error occurs
     */
    public void updateZone(Zone zone) throws SQLException {
        requireAdminRole();
        if (zone.getZoneId() <= 0) {
            throw new IllegalArgumentException("zoneId must be a positive integer");
        }
        if (zone.getName() == null || zone.getName().isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (zone.getPrice() == null || zone.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            zoneDAO.update(conn, zone);
            LOGGER.info("Admin '{}' updated zone '{}'", adminUsername, zone.getName());
        } catch (SQLException e) {
            LOGGER.error("Failed to update zone '{}'", zone.getName(), e);
            throw e;
        }
    }

    /**
     * Deletes a zone and (via ON DELETE CASCADE) all its associated seats.
     *
     * @param zoneId the ID of the zone to delete; must be positive
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if {@code zoneId} is not positive
     * @throws SQLException             if a database access error occurs
     */
    public void deleteZone(int zoneId) throws SQLException {
        requireAdminRole();
        if (zoneId <= 0) {
            throw new IllegalArgumentException("zoneId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            zoneDAO.delete(conn, zoneId);
            LOGGER.info("Admin '{}' deleted zone id '{}'", adminUsername, zoneId);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete zone id '{}'", zoneId, e);
            throw e;
        }
    }

    /**
     * Returns all zones for the specified event ordered by {@code zone_id ASC}.
     *
     * @param eventId the event to retrieve zones for
     * @return list of zones; never {@code null}, may be empty
     * @throws SQLException if a database access error occurs
     */
    public List<Zone> getZonesByEvent(int eventId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return zoneDAO.findByEventId(conn, eventId);
        } catch (SQLException e) {
            LOGGER.error("Failed to load zones for event '{}'", eventId, e);
            throw e;
        }
    }

    /**
     * Returns the number of seats belonging to the specified zone.
     *
     * <p>Executes {@code SELECT COUNT(*) FROM seats WHERE zone_id = ?} inline to
     * avoid adding methods to {@link com.ticketsync.dao.SeatDAO} that would break
     * existing test mocks.
     *
     * @param zoneId the zone to count seats for
     * @return the seat count; 0 if no seats exist
     * @throws SQLException if a database access error occurs
     */
    public int countSeatsForZone(int zoneId) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT_SEATS)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
