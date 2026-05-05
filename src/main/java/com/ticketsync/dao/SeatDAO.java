package com.ticketsync.dao;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Seat database operations.
 * 
 * <h2>Transaction Participation</h2>
 * All methods accept a Connection parameter to participate in service-layer transactions.
 * Implementations use PreparedStatement for SQL injection prevention.
 * 
 * <h2>Database Mapping</h2>
 * <ul>
 *   <li>Database Table: seats</li>
 *   <li>Primary Key: seat_id (SERIAL)</li>
 *   <li>Foreign Keys: zone_id → zones.zone_id, sale_id → sales.sale_id</li>
 * </ul>
 * 
 * <h2>Concurrency Control</h2>
 * The {@link #selectForUpdate} method is CRITICAL for preventing oversells.
 * It uses PostgreSQL row-level locking with SELECT FOR UPDATE to guarantee
 * exclusive access to seats during purchase transactions.
 * 
 * @see com.ticketsync.model.Seat
 * @see SeatStatus
 */
public interface SeatDAO {
    
    /**
     * Finds a seat by primary key.
     * 
     * @param conn Active database connection
     * @param seatId Primary key of seat to retrieve
     * @return Optional containing Seat if found, empty otherwise
     * @throws SQLException if database access error occurs
     * @throws IllegalArgumentException if seatId is zero or negative
     */
    Optional<Seat> findById(Connection conn, int seatId) throws SQLException;
    
    /**
     * Retrieves all seats for a specific zone.
     * Used in admin seating layout editor.
     * 
     * @param conn Active database connection
     * @param zoneId Zone ID to retrieve seats for
     * @return List of seats in zone, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<Seat> findByZoneId(Connection conn, int zoneId) throws SQLException;
    
    /**
     * Retrieves all seats for a specific event (across all zones).
     * Used in vendor POS seat map display.
     * 
     * @param conn Active database connection
     * @param eventId Event ID to retrieve seats for
     * @return List of seats for event, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<Seat> findByEventId(Connection conn, int eventId) throws SQLException;
    
    /**
     * Locks seats for update in SERIALIZABLE transaction, preventing concurrent modifications.
     * Uses PostgreSQL row-level locking (SELECT ... FOR UPDATE) to guarantee exclusive access.
     * 
     * <p><strong>CRITICAL METHOD:</strong> This is the most important method for preventing oversells.
     * It MUST be called first in any seat purchase transaction to prevent race conditions.
     * 
     * <h4>Implementation Pattern</h4>
     * <pre>{@code
     * String sql = "SELECT * FROM seats WHERE seat_id = ANY(?) FOR UPDATE";
     * PreparedStatement stmt = conn.prepareStatement(sql);
     * stmt.setArray(1, conn.createArrayOf("INTEGER", seatIds.toArray()));
     * ResultSet rs = stmt.executeQuery();
     * return mapResultSet(rs);
     * }</pre>
     * 
     * <h4>Locking Behavior</h4>
     * <ul>
     *   <li>Other transactions attempting to SELECT FOR UPDATE same seats will WAIT</li>
     *   <li>SERIALIZABLE isolation + FOR UPDATE = Zero oversells guaranteed</li>
     *   <li>Locks held until Connection.commit() or Connection.rollback()</li>
     * </ul>
     * 
     * @param conn Active database connection with SERIALIZABLE isolation level set
     * @param seatIds List of seat IDs to lock for purchase
     * @return List of Seat objects with current status (locked exclusively to this connection)
     * @throws SQLException if seats don't exist or connection fails
     * @throws IllegalArgumentException if seatIds is null, empty, or contains null elements
     * @throws IllegalStateException if connection isolation level is not TRANSACTION_SERIALIZABLE
     */
    List<Seat> selectForUpdate(Connection conn, List<Integer> seatIds) throws SQLException;
    
    /**
     * Atomically updates seat status and sale reference.
     * Used in purchase transaction to mark seats as SOLD.
     * 
     * <p>This method is called AFTER {@link #selectForUpdate} in the transaction.
     * 
     * @param conn Active database connection
     * @param seatIds List of seat IDs to update
     * @param status New seat status (typically SOLD)
     * @param saleId Sale ID to associate with seats (null for AVAILABLE/DISABLED/RESERVED status)
     * @throws SQLException if database access error occurs
     * @throws IllegalArgumentException if seatIds is null or empty, status is null, or status is
     *         SOLD with a null saleId
     */
    void updateStatus(Connection conn, List<Integer> seatIds, SeatStatus status, Integer saleId)
            throws SQLException;
    
    /**
     * Inserts a new seat into the database.
     * Used in admin seating layout editor.
     * 
     * @param conn Active database connection
     * @param seat Seat object to insert (seatId field is ignored; database generates the key)
     * @return Generated seat_id from database
     * @throws SQLException if database access error or constraint violation occurs
     * @throws IllegalArgumentException if seat is null
     */
    int insert(Connection conn, Seat seat) throws SQLException;
    
    /**
     * Deletes a seat from the database.
     * Used in admin seating layout editor when removing seats.
     * 
     * <p><strong>Note:</strong> Deletion may fail due to foreign key constraints
     * if seat has been sold (sale_id foreign key).
     * 
     * @param conn Active database connection
     * @param seatId Primary key of seat to delete
     * @throws SQLException if database access error or constraint violation occurs
     */
    void delete(Connection conn, int seatId) throws SQLException;
}
