package com.ticketsync.dao;

import com.ticketsync.model.Event;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Event database operations.
 * 
 * <h2>Transaction Participation</h2>
 * All methods accept a Connection parameter to participate in service-layer transactions.
 * Implementations use PreparedStatement for SQL injection prevention.
 * 
 * <h2>Database Mapping</h2>
 * <ul>
 *   <li>Database Table: events</li>
 *   <li>Primary Key: event_id (SERIAL)</li>
 *   <li>Foreign Keys: created_by → users.user_id</li>
 * </ul>
 * 
 * @see com.ticketsync.model.Event
 */
public interface EventDAO {
    
    /**
     * Finds an event by primary key.
     * 
     * @param conn Active database connection
     * @param eventId Primary key of event to retrieve
     * @return Optional containing Event if found, empty otherwise
     * @throws SQLException if database access error occurs
     * @throws IllegalArgumentException if eventId is zero or negative
     */
    Optional<Event> findById(Connection conn, int eventId) throws SQLException;
    
    /**
     * Retrieves all events in the system.
     * Used in admin event management interface.
     * 
     * @param conn Active database connection
     * @return List of all events, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<Event> findAll(Connection conn) throws SQLException;
    
    /**
     * Retrieves only active events for vendor POS event selector.
     * An event is active if {@code is_active = true}.
     * Used in vendor POS interface.
     * 
     * @param conn Active database connection
     * @return List of active events, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<Event> findActive(Connection conn) throws SQLException;
    
    /**
     * Inserts a new event into the database.
     * 
     * @param conn Active database connection
     * @param event Event object to insert (eventId field is ignored; database generates the key)
     * @return Generated event_id from database
     * @throws SQLException if database access error or constraint violation occurs
     * @throws IllegalArgumentException if event is null
     */
    int insert(Connection conn, Event event) throws SQLException;
    
    /**
     * Updates an existing event's information.
     * Used in admin event management to modify details or toggle active status.
     * 
     * @param conn Active database connection
     * @param event Event object with updated fields (eventId must be set)
     * @throws SQLException if database access error or event not found
     */
    void update(Connection conn, Event event) throws SQLException;
    
    /**
     * Deletes an event from the database.
     * Used in admin event management interface.
     * 
     * <p><strong>Note:</strong> Deletion may fail due to foreign key constraints
     * if event has associated zones, seats, or sales records.
     * 
     * @param conn Active database connection
     * @param eventId Primary key of event to delete
     * @throws SQLException if database access error or constraint violation occurs
     */
    void delete(Connection conn, int eventId) throws SQLException;
}
