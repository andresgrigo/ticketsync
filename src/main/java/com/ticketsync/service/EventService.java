package com.ticketsync.service;

import com.ticketsync.dao.EventDAO;
import com.ticketsync.dao.EventDAOImpl;
import com.ticketsync.model.Event;
import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Provides a {@link Connection} for each service operation.
 *
 * <p>The production implementation delegates to {@link DatabaseConfig#getConnection()}.
 * Tests supply a no-op substitute so that unit tests run without a live database.
 */
@FunctionalInterface
interface ConnectionFactory {
    Connection get() throws SQLException;
}

/**
 * Service class for event management business logic.
 *
 * <p>Provides create, update, delete, activate/deactivate, and query operations
 * on the {@code events} table, delegating persistence to {@link EventDAO}. All
 * methods acquire their own {@link Connection} via
 * {@link DatabaseConfig#getConnection()} and release it via try-with-resources.
 *
 * <p>All mutating operations require an active ADMIN session in
 * {@link SessionContext}. A {@link SecurityException} is thrown if the caller
 * does not have the {@code ADMIN} role.
 *
 * <p>All mutating operations log an audit trail entry at INFO level.
 * {@link SQLException} from DAO calls is caught, logged at ERROR level
 * and re-thrown.
 */
public class EventService {

    private static final Logger LOGGER = LogManager.getLogger(EventService.class);

    private final EventDAO eventDAO;
    private final ConnectionFactory connFactory;

    /**
     * Production constructor — creates a live {@link EventDAOImpl} instance and
     * uses {@link DatabaseConfig#getConnection()} for connection acquisition.
     */
    public EventService() {
        this.eventDAO = new EventDAOImpl();
        this.connFactory = DatabaseConfig::getConnection;
    }

    /**
     * Package-private constructor for test injection.
     *
     * <p>Accepts a custom {@link EventDAO} and falls back to the live
     * {@link DatabaseConfig#getConnection()} for connection acquisition.
     * Use the three-argument form when a no-op connection is also required.
     *
     * @param eventDAO the DAO implementation to use; must not be {@code null}
     */
    EventService(EventDAO eventDAO) {
        this.eventDAO = eventDAO;
        this.connFactory = DatabaseConfig::getConnection;
    }

    /**
     * Package-private constructor for full unit-test injection (no DB required).
     *
     * <p>Allows pure unit tests to inject both a stub {@link EventDAO} and a
     * no-op {@link ConnectionFactory} so that tests never touch the database.
     *
     * @param eventDAO    the DAO stub; must not be {@code null}
     * @param connFactory the connection provider stub; must not be {@code null}
     */
    EventService(EventDAO eventDAO, ConnectionFactory connFactory) {
        this.eventDAO = eventDAO;
        this.connFactory = connFactory;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Verifies that the current session belongs to a user with the ADMIN role.
     *
     * @throws SecurityException if no user is authenticated or the authenticated
     *                           user does not hold the ADMIN role
     */
    private void requireAdminRole() {
        if (!SessionContext.hasRole("ADMIN")) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
    }

    /**
     * Verifies that a user is currently authenticated (any role).
     *
     * @throws SecurityException if no user is authenticated
     */
    private void requireAuthenticated() {
        if (SessionContext.getCurrentUser().isEmpty()) {
            throw new SecurityException("Access denied: authentication required");
        }
    }

    /**
     * Validates that the required event fields are present and logically correct.
     *
     * @param event the event to validate; must not be {@code null}
     * @throws IllegalArgumentException if any required field is null, blank, or
     *                                  the event date is not in the future
     */
    private void validateEventFields(Event event) {
        if (event.getName() == null || event.getName().isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (event.getEventDate() == null || !event.getEventDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("eventDate must be a future date/time");
        }
        if (event.getVenue() == null || event.getVenue().isBlank()) {
            throw new IllegalArgumentException("venue must not be null or blank");
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Creates a new event in the database.
     *
     * <p>Requires an active ADMIN session. The {@code createdBy} field of the
     * supplied event is set to the current user's ID before the insert.
     *
     * @param event the event to create; required fields: name, eventDate (future), venue
     * @return the generated {@code event_id}
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if any required field is invalid
     * @throws SQLException             if a database access error occurs
     */
    public int createEvent(Event event) throws SQLException {
        requireAdminRole();
        validateEventFields(event);
        event.setCreatedBy(SessionContext.getCurrentUser().orElseThrow(() -> new SecurityException("No active session")).getUserId());
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            int newId = eventDAO.insert(conn, event);
            LOGGER.info("Admin '{}' created event '{}'", adminUsername, event.getName());
            return newId;
        } catch (SQLException e) {
            LOGGER.error("Failed to create event '{}'", event.getName(), e);
            throw e;
        }
    }

    /**
     * Updates an existing event in the database.
     *
     * <p>Requires an active ADMIN session and a valid (positive) event ID.
     * Throws {@link IllegalArgumentException} if the event does not exist.
     *
     * @param event the event to update; {@code eventId} must be positive
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if {@code eventId} is not positive, or
     *                                  the event does not exist in the database
     * @throws SQLException             if a database access error occurs
     */
    public void updateEvent(Event event) throws SQLException {
        requireAdminRole();
        if (event.getEventId() <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        validateEventFields(event);
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            int eventId = event.getEventId();
            eventDAO.findById(conn, eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            eventDAO.update(conn, event);
            LOGGER.info("Admin '{}' updated event '{}'", adminUsername, event.getName());
        } catch (SQLException e) {
            LOGGER.error("Failed to update event '{}'", event.getName(), e);
            throw e;
        }
    }

    /**
     * Deletes an event from the database.
     *
     * <p>Requires an active ADMIN session and a valid (positive) event ID.
     *
     * @param eventId the ID of the event to delete; must be positive
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if {@code eventId} is not positive
     * @throws SQLException             if a database access error occurs
     */
    public void deleteEvent(int eventId) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            eventDAO.delete(conn, eventId);
            LOGGER.info("Admin '{}' deleted event id '{}'", adminUsername, eventId);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete event id '{}'", eventId, e);
            throw e;
        }
    }

    /**
     * Activates an event so it appears in the vendor POS event selector.
     *
     * <p>Loads the full event from the database, sets {@code isActive = true},
     * and persists the change via {@link EventDAO#update(Connection, Event)}.
     *
     * @param eventId the ID of the event to activate; must be positive
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if the event does not exist
     * @throws SQLException             if a database access error occurs
     */
    public void activateEvent(int eventId) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            Event event = eventDAO.findById(conn, eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            event.setActive(true);
            eventDAO.update(conn, event);
            LOGGER.info("Admin '{}' activated event '{}'", adminUsername, event.getName());
        } catch (SQLException e) {
            LOGGER.error("Failed to activate event id '{}'", eventId, e);
            throw e;
        }
    }

    /**
     * Deactivates an event so it no longer appears in the vendor POS event selector.
     *
     * <p>Loads the full event from the database, sets {@code isActive = false},
     * and persists the change via {@link EventDAO#update(Connection, Event)}.
     *
     * @param eventId the ID of the event to deactivate; must be positive
     * @throws SecurityException        if the current user does not have the ADMIN role
     * @throws IllegalArgumentException if the event does not exist
     * @throws SQLException             if a database access error occurs
     */
    public void deactivateEvent(int eventId) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            Event event = eventDAO.findById(conn, eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            event.setActive(false);
            eventDAO.update(conn, event);
            LOGGER.info("Admin '{}' deactivated event '{}'", adminUsername, event.getName());
        } catch (SQLException e) {
            LOGGER.error("Failed to deactivate event id '{}'", eventId, e);
            throw e;
        }
    }

    /**
     * Returns all active events.
     *
     * <p>Requires an authenticated session (any role — vendors need this for the POS event selector).
     *
     * @return list of active events; never {@code null}, may be empty
     * @throws SecurityException if no user is authenticated
     * @throws SQLException if a database access error occurs
     */
    public List<Event> getActiveEvents() throws SQLException {
        requireAuthenticated();
        try (Connection conn = connFactory.get()) {
            List<Event> result = eventDAO.findActive(conn);
            return result != null ? result : Collections.emptyList();
        } catch (SQLException e) {
            LOGGER.error("Failed to retrieve active events", e);
            throw e;
        }
    }

    /**
     * Returns all events ordered by {@code event_date DESC}.
     *
     * <p>Requires an active ADMIN session.
     *
     * @return list of all events; never {@code null}, may be empty
     * @throws SecurityException if the current user does not have the ADMIN role
     * @throws SQLException      if a database access error occurs
     */
    public List<Event> findAllEvents() throws SQLException {
        requireAdminRole();
        try (Connection conn = connFactory.get()) {
            return eventDAO.findAll(conn);
        } catch (SQLException e) {
            LOGGER.error("Failed to retrieve all events", e);
            throw e;
        }
    }
}
