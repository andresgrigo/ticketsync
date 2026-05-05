package com.ticketsync.dao;

import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * JDBC implementation of {@link SaleDAO} for the {@code sales} and {@code sale_items} tables.
 *
 * <p>All SQL is executed via {@link PreparedStatement} using {@code ?} placeholders.
 * No SQL is ever built by string concatenation, preventing SQL injection (OWASP A03).
 *
 * <p>Callers are responsible for managing the {@link Connection} lifecycle (open,
 * commit/rollback, close). This class never closes the supplied connection.
 *
 * <p>Transaction coordination (autoCommit=false, SERIALIZABLE isolation, commit/rollback)
 * is the responsibility of the service layer (TransactionService).
 *
 * @see SaleDAO
 * @see com.ticketsync.model.Sale
 * @see com.ticketsync.model.SaleItem
 */
public class SaleDAOImpl implements SaleDAO {

    /** Creates a new {@code SaleDAOImpl} using the default connection factory. */
    public SaleDAOImpl() {
    }

    // -------------------------------------------------------------------------
    // SQL constants
    // -------------------------------------------------------------------------

    private static final String SQL_INSERT_SALE =
            "INSERT INTO sales (event_id, vendor_id, total_amount, sale_timestamp, booth_id) "
            + "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_INSERT_SALE_ITEM =
            "INSERT INTO sale_items (sale_id, seat_id, price_paid) VALUES (?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
            "SELECT sale_id, event_id, vendor_id, total_amount, sale_timestamp, booth_id "
            + "FROM sales WHERE sale_id = ?";

    private static final String SQL_FIND_BY_EVENT_ID =
            "SELECT sale_id, event_id, vendor_id, total_amount, sale_timestamp, booth_id "
            + "FROM sales WHERE event_id = ? ORDER BY sale_timestamp DESC";

    private static final String SQL_FIND_SALE_ITEMS_BY_SALE_ID =
            "SELECT sale_item_id, sale_id, seat_id, price_paid "
            + "FROM sale_items WHERE sale_id = ? ORDER BY sale_item_id ASC";

    private static final String SQL_FIND_BY_VENDOR =
            "SELECT sale_id, event_id, vendor_id, total_amount, sale_timestamp, booth_id "
            + "FROM sales WHERE vendor_id = ? AND sale_timestamp::date = ? ORDER BY sale_timestamp DESC";

    // -------------------------------------------------------------------------
    // Logger
    // -------------------------------------------------------------------------

    private static final Logger LOGGER = LogManager.getLogger(SaleDAOImpl.class);

    // -------------------------------------------------------------------------
    // Public interface methods
    // -------------------------------------------------------------------------

    /**
     * Finds a sale by primary key.
     *
     * @param conn   Active database connection
     * @param saleId Primary key of the sale to retrieve
     * @return {@code Optional} containing the {@link Sale} if found, empty otherwise
     * @throws SQLException             if a database access error occurs
     * @throws IllegalArgumentException if {@code saleId} is zero or negative
     */
    @Override
    public Optional<Sale> findById(Connection conn, int saleId) throws SQLException {
        if (saleId <= 0) {
            throw new IllegalArgumentException("saleId must be positive, got: " + saleId);
        }
        LOGGER.debug("Finding sale by id={}", saleId);
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
    * Retrieves all sales for a specific event, ordered by {@code sale_timestamp DESC}.
     *
     * @param conn    Active database connection
     * @param eventId Event ID to retrieve sales for
     * @return List of sales for the event; empty list if none exist
     * @throws SQLException if a database access error occurs
     */
    @Override
    public List<Sale> findByEventId(Connection conn, int eventId) throws SQLException {
        LOGGER.debug("Finding sales by eventId={}", eventId);
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_EVENT_ID)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapRow(rs));
                }
            }
        }
        return sales;
    }

    @Override
    public List<SaleItem> findSaleItemsBySaleId(Connection conn, int saleId) throws SQLException {
        if (saleId <= 0) {
            throw new IllegalArgumentException("saleId must be positive, got: " + saleId);
        }
        LOGGER.debug("Finding sale items by saleId={}", saleId);
        List<SaleItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_SALE_ITEMS_BY_SALE_ID)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapSaleItem(rs));
                }
            }
        }
        return items;
    }

    /**
    * Retrieves all sales by a specific vendor on a specific date.
     *
     * <p>Uses PostgreSQL's {@code ::date} cast to compare the {@code sale_timestamp}
     * column against the supplied date. Note: the cast uses the database server's local
     * date — callers should ensure the supplied date matches the server's timezone (typically UTC)
     * to avoid off-by-one errors at midnight boundaries.
     *
     * @param conn     Active database connection
     * @param vendorId Vendor user ID
     * @param date     Calendar date to match against {@code sale_timestamp::date}
     * @return List of sales by the vendor on the given date; empty list if none exist
     * @throws SQLException if a database access error occurs
     */
    @Override
    public List<Sale> findByVendor(Connection conn, int vendorId, LocalDate date) throws SQLException {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        LOGGER.debug("Finding sales by vendorId={}, date={}", vendorId, date);
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_VENDOR)) {
            ps.setInt(1, vendorId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapRow(rs));
                }
            }
        }
        return sales;
    }

    /**
    * Inserts a new sale record and returns the database-generated {@code sale_id}.
     *
     * <p>This method is called within a transaction AFTER seat availability is validated
     * and BEFORE seat status is updated. See {@link SaleDAO} class documentation for the
     * complete transaction flow.
     *
     * @param conn Active database connection
     * @param sale Sale to insert; the {@code saleId} field is ignored — the database generates it
     * @return Generated {@code sale_id}
     * @throws SQLException             if a database access error or constraint violation occurs
     * @throws IllegalArgumentException if {@code sale} is null, or if {@code sale.saleTimestamp} is null
     */
    @Override
    public int insert(Connection conn, Sale sale) throws SQLException {
        if (sale == null) {
            throw new IllegalArgumentException("sale must not be null");
        }
        if (sale.getSaleTimestamp() == null) {
            throw new IllegalArgumentException("sale.saleTimestamp must not be null");
        }
        LOGGER.debug("Inserting sale for event={}, vendor={}", sale.getEventId(), sale.getVendorId());
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_SALE, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sale.getEventId());
            ps.setInt(2, sale.getVendorId());
            ps.setBigDecimal(3, sale.getTotalAmount());
            ps.setTimestamp(4, Timestamp.valueOf(sale.getSaleTimestamp()));
            ps.setString(5, sale.getBoothId());
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
    * Inserts sale item records linking seats to a sale.
     *
     * <p>This method is called within the same transaction as {@link #insert},
     * immediately after the Sale record is created.
     *
     * @param conn   Active database connection
     * @param saleId Sale ID to associate items with; must be positive
     * @param items  List of {@link SaleItem} objects to insert; must be non-null and non-empty
     * @throws SQLException             if a database access error or constraint violation occurs
     * @throws IllegalArgumentException if {@code saleId} is zero or negative, or if {@code items} is null or empty
     */
    @Override
    public void insertSaleItems(Connection conn, int saleId, List<SaleItem> items) throws SQLException {
        if (saleId <= 0) {
            throw new IllegalArgumentException("saleId must be positive, got: " + saleId);
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }
        LOGGER.debug("Inserting {} sale items for saleId={}", items.size(), saleId);
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_SALE_ITEM)) {
            for (SaleItem item : items) {
                ps.setInt(1, saleId);
                ps.setInt(2, item.getSeatId());
                ps.setBigDecimal(3, item.getPricePaid());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps the current row of a {@link ResultSet} to a {@link Sale} object.
     *
     * <p>The nullable {@code booth_id} column is handled by {@link ResultSet#getString},
     * which returns {@code null} for SQL NULL values.
     *
     * @param rs ResultSet positioned on the current row
     * @return populated {@link Sale} instance
     * @throws SQLException if a column cannot be read
     */
    private Sale mapRow(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setSaleId(rs.getInt("sale_id"));
        sale.setEventId(rs.getInt("event_id"));
        sale.setVendorId(rs.getInt("vendor_id"));
        sale.setTotalAmount(rs.getBigDecimal("total_amount"));
        Timestamp ts = rs.getTimestamp("sale_timestamp");
        sale.setSaleTimestamp(ts != null ? ts.toLocalDateTime() : null);
        sale.setBoothId(rs.getString("booth_id"));
        return sale;
    }

    private SaleItem mapSaleItem(ResultSet rs) throws SQLException {
        SaleItem item = new SaleItem();
        item.setSaleItemId(rs.getInt("sale_item_id"));
        item.setSaleId(rs.getInt("sale_id"));
        item.setSeatId(rs.getInt("seat_id"));
        item.setPricePaid(rs.getBigDecimal("price_paid"));
        return item;
    }
}
