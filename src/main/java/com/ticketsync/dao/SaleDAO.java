package com.ticketsync.dao;

import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Sale database operations.
 * 
 * <h2>Transaction Participation</h2>
 * All methods accept a Connection parameter to participate in service-layer transactions.
 * Implementations use PreparedStatement for SQL injection prevention.
 * 
 * <h2>Database Mapping</h2>
 * <ul>
 *   <li>Database Table: sales</li>
 *   <li>Primary Key: sale_id (SERIAL)</li>
 *   <li>Foreign Keys: event_id → events.event_id, vendor_id → users.user_id</li>
 * </ul>
 * 
 * <h2>Transaction Semantics</h2>
 * Sale insertion is part of a multi-step atomic transaction:
 * <ol>
 *   <li>Lock seats with SeatDAO.selectForUpdate()</li>
 *   <li>Validate seat availability</li>
 *   <li>Insert Sale record (this DAO's insert method)</li>
 *   <li>Insert SaleItem records (this DAO's insertSaleItems method)</li>
 *   <li>Update seat status with SeatDAO.updateStatus()</li>
 *   <li>Commit transaction</li>
 * </ol>
 * 
 * @see com.ticketsync.model.Sale
 * @see SaleItem
 */
public interface SaleDAO {
    
    /**
     * Finds a sale by primary key.
     * 
     * @param conn Active database connection
     * @param saleId Primary key of sale to retrieve
     * @return Optional containing Sale if found, empty otherwise
     * @throws SQLException if database access error occurs
     * @throws IllegalArgumentException if saleId is zero or negative
     */
    Optional<Sale> findById(Connection conn, int saleId) throws SQLException;
    
    /**
     * Retrieves all sales for a specific event.
     * Used in admin sales reporting (future story).
     * 
     * @param conn Active database connection
     * @param eventId Event ID to retrieve sales for
     * @return List of sales for event, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<Sale> findByEventId(Connection conn, int eventId) throws SQLException;
    
    /**
     * Retrieves all sales by a specific vendor on a specific date.
     * Used in vendor daily sales report (future story).
     * 
     * @param conn Active database connection
     * @param vendorId Vendor user ID
     * @param date Date to retrieve sales for. Implementations must compare against the
     *        {@code sale_timestamp} column cast to the database server's local date.
     *        Callers should ensure the date reflects the server's timezone (typically UTC)
     *        to avoid off-by-one errors at midnight boundaries.
     * @return List of sales by vendor on date, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<Sale> findByVendor(Connection conn, int vendorId, LocalDate date) throws SQLException;
    
    /**
     * Inserts a new sale record into the database.
     * 
     * <p>This method is called within a transaction AFTER seat availability is validated
     * and BEFORE seat status is updated. See {@link SaleDAO} class documentation for
     * complete transaction flow.
     * 
     * @param conn Active database connection
     * @param sale Sale object to insert (saleId field is ignored; database generates the key)
     * @return Generated sale_id from database
     * @throws SQLException if database access error or constraint violation occurs
     * @throws IllegalArgumentException if sale is null
     */
    int insert(Connection conn, Sale sale) throws SQLException;
    
    /**
     * Inserts sale item records linking seats to a sale.
     * 
     * <p>This method is called within the same transaction as {@link #insert},
     * immediately after the Sale record is created.
     * 
     * @param conn Active database connection
     * @param saleId Sale ID to associate items with
     * @param items List of SaleItem objects to insert (saleItemId field is ignored;
     *        database generates each key). Must be non-null and non-empty.
     * @throws SQLException if database access error or constraint violation occurs
     * @throws IllegalArgumentException if items is null or empty
     */
    void insertSaleItems(Connection conn, int saleId, List<SaleItem> items) throws SQLException;
}
