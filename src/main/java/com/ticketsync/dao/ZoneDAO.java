package com.ticketsync.dao;

import com.ticketsync.model.Zone;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Zone database operations.
 * 
 * <h2>Transaction Participation</h2>
 * All methods accept a Connection parameter to participate in service-layer transactions.
 * Implementations use PreparedStatement for SQL injection prevention.
 * 
 * <h2>Database Mapping</h2>
 * <ul>
 *   <li>Database Table: zones</li>
 *   <li>Primary Key: zone_id (SERIAL)</li>
 *   <li>Foreign Keys: event_id → events.event_id</li>
 * </ul>
 * 
 * @see com.ticketsync.model.Zone
 */
public interface ZoneDAO {
    
    /**
     * Finds a zone by primary key.
     * 
     * @param conn Active database connection
     * @param zoneId Primary key of zone to retrieve
     * @return Optional containing Zone if found, empty otherwise
     * @throws SQLException if database access error occurs
     * @throws IllegalArgumentException if zoneId is zero or negative
     */
    Optional<Zone> findById(Connection conn, int zoneId) throws SQLException;
    
    /**
     * Retrieves all zones for a specific event.
     * Used in admin zone configuration and seating layout editor.
     * 
     * @param conn Active database connection
     * @param eventId Event ID to retrieve zones for
     * @return List of zones for event, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<Zone> findByEventId(Connection conn, int eventId) throws SQLException;
    
    /**
     * Inserts a new zone into the database.
     * 
     * @param conn Active database connection
     * @param zone Zone object to insert (zoneId field is ignored; database generates the key)
     * @return Generated zone_id from database
     * @throws SQLException if database access error or constraint violation occurs
     * @throws IllegalArgumentException if zone is null
     */
    int insert(Connection conn, Zone zone) throws SQLException;
    
    /**
     * Updates an existing zone's information.
     * Used in admin zone configuration to modify names or prices.
     * 
     * @param conn Active database connection
     * @param zone Zone object with updated fields (zoneId must be set)
     * @throws SQLException if database access error or zone not found
     */
    void update(Connection conn, Zone zone) throws SQLException;
    
    /**
     * Deletes a zone from the database.
     * Used in admin zone configuration interface.
     * 
     * <p><strong>Note:</strong> Deletion may fail due to foreign key constraints
     * if zone has associated seats.
     * 
     * @param conn Active database connection
     * @param zoneId Primary key of zone to delete
     * @throws SQLException if database access error or constraint violation occurs
     */
    void delete(Connection conn, int zoneId) throws SQLException;
}
