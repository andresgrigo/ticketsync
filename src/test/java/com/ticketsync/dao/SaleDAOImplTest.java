package com.ticketsync.dao;

import com.ticketsync.model.Event;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link SaleDAOImpl}.
 *
 * <p><strong>Prerequisites:</strong> PostgreSQL running on localhost:5432,
 * database {@code ticketsync} exists, Flyway migrations applied.
 *
 * <p><strong>Enable tests:</strong>
 * <pre>
 * # PowerShell
 * $env:TICKETSYNC_MASTER_KEY="your-key"; $env:DB_TEST_ENABLED="true"; mvn test
 * </pre>
 *
 * <p>Each test inserts its own rows using a time-stamped prefix and cleans them up in
 * {@link #tearDown()} to avoid collisions with existing data.
 */
class SaleDAOImplTest {

    private static final Logger LOGGER = LogManager.getLogger(SaleDAOImplTest.class);

    private SaleDAOImpl dao;
    private SeatDAOImpl seatDao;
    private ZoneDAOImpl zoneDao;
    private EventDAOImpl eventDao;
    private Connection conn;

    /** Unique prefix avoids collisions between test runs and parallel suites. */
    private final String prefix = "test_sale_" + System.currentTimeMillis();

    /** Tracks all sale_ids inserted during a test so tearDown can clean up. */
    private final List<Integer> insertedSaleIds = new ArrayList<>();

    /** Tracks all seat_ids inserted during a test. */
    private final List<Integer> insertedSeatIds = new ArrayList<>();

    /** Parent zone for this test; cascade-deleted in tearDown. */
    private int insertedZoneId = -1;

    /** Parent event for this test; cascade-deleted in tearDown. */
    private int insertedEventId = -1;

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");
        dao = new SaleDAOImpl();
        seatDao = new SeatDAOImpl();
        zoneDao = new ZoneDAOImpl();
        eventDao = new EventDAOImpl();
        conn = DatabaseConfig.getConnection();
        conn.setAutoCommit(true);
        insertedEventId = insertTestEvent(conn, prefix);
        insertedZoneId = insertTestZone(conn, insertedEventId, prefix);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null) {
            try {
                // Delete sales first (cascades sale_items via ON DELETE CASCADE).
                // Each deletion is individually guarded so one failure does not abort cleanup.
                for (int id : insertedSaleIds) {
                    if (id > 0) {
                        try {
                            deleteSale(conn, id);
                        } catch (SQLException e) {
                            LOGGER.warn("tearDown: could not delete sale id={}", id, e);
                        }
                    }
                }
                for (int id : insertedSeatIds) {
                    if (id > 0) {
                        try {
                            seatDao.delete(conn, id);
                        } catch (SQLException e) {
                            LOGGER.warn("tearDown: could not delete seat id={}", id, e);
                        }
                    }
                }
                if (insertedZoneId > 0) {
                    zoneDao.delete(conn, insertedZoneId);
                }
                if (insertedEventId > 0) {
                    eventDao.delete(conn, insertedEventId);
                }
            } finally {
                conn.close();
            }
        }
        insertedSaleIds.clear();
        insertedSeatIds.clear();
    }

    // -------------------------------------------------------------------------
    // insert
    // -------------------------------------------------------------------------

    /**
     * insert() should return a positive generated sale_id.
     */
    @Test
    void insert_returnsPositiveGeneratedSaleId() throws SQLException {
        Sale sale = buildSale(insertedEventId, 1, "10.00", LocalDateTime.now(), "Booth-1");

        int id = dao.insert(conn, sale);
        insertedSaleIds.add(id);

        assertTrue(id > 0, "Generated sale_id should be positive");
    }

    /**
     * insert() should throw IllegalArgumentException for a null sale.
     */
    @Test
    void insert_nullSale_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.insert(conn, null));
    }

    // -------------------------------------------------------------------------
    // insertSaleItems
    // -------------------------------------------------------------------------

    /**
     * insertSaleItems() should persist all provided items to the database.
     */
    @Test
    void insertSaleItems_persistsAllItems() throws SQLException {
        // Need seats for FK constraint
        int seatId1 = insertTestSeat(conn, insertedZoneId, prefix + "_R1", "1");
        int seatId2 = insertTestSeat(conn, insertedZoneId, prefix + "_R1", "2");
        insertedSeatIds.add(seatId1);
        insertedSeatIds.add(seatId2);

        Sale sale = buildSale(insertedEventId, 1, "20.00", LocalDateTime.now(), null);
        int saleId = dao.insert(conn, sale);
        insertedSaleIds.add(saleId);

        List<SaleItem> items = List.of(
                buildSaleItem(seatId1, "10.00"),
                buildSaleItem(seatId2, "10.00")
        );
        dao.insertSaleItems(conn, saleId, items);

        // Verify via direct count query
        int count = countSaleItems(conn, saleId);
        assertEquals(2, count, "Both sale items should be persisted");
    }

    /**
     * insertSaleItems() should throw IllegalArgumentException for empty items list.
     */
    @Test
    void insertSaleItems_emptyList_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.insertSaleItems(conn, 1, List.of()));
    }

    /**
     * insertSaleItems() should throw IllegalArgumentException for null items.
     */
    @Test
    void insertSaleItems_nullList_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.insertSaleItems(conn, 1, null));
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    /**
     * findById() should return a fully-populated Sale for an existing record.
     */
    @Test
    void findById_existingSale_returnsFullyPopulatedSale() throws SQLException {
        LocalDateTime timestamp = LocalDateTime.now().withNano(0); // truncate nanos for DB comparison
        Sale sale = buildSale(insertedEventId, 1, "50.00", timestamp, "Booth-2");
        int saleId = dao.insert(conn, sale);
        insertedSaleIds.add(saleId);

        Optional<Sale> found = dao.findById(conn, saleId);

        assertTrue(found.isPresent(), "findById should return a sale for a valid id");
        Sale result = found.get();
        assertEquals(saleId, result.getSaleId());
        assertEquals(insertedEventId, result.getEventId());
        assertEquals(1, result.getVendorId());
        assertEquals(new BigDecimal("50.00"), result.getTotalAmount());
        assertNotNull(result.getSaleTimestamp(), "sale_timestamp must not be null");
        assertEquals("Booth-2", result.getBoothId());
    }

    /**
     * findById() should return Optional.empty() for a non-existent id.
     */
    @Test
    void findById_nonExistentId_returnsEmpty() throws SQLException {
        Optional<Sale> found = dao.findById(conn, Integer.MAX_VALUE);
        assertFalse(found.isPresent(), "findById should return empty for non-existent id");
    }

    /**
     * findById() should throw IllegalArgumentException for a non-positive id.
     */
    @Test
    void findById_nonPositiveId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.findById(conn, 0));
    }

    // -------------------------------------------------------------------------
    // findByEventId
    // -------------------------------------------------------------------------

    /**
     * findByEventId() should return all sales for the given event.
     */
    @Test
    void findByEventId_returnsSalesForEvent() throws SQLException {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(10).withNano(0);
        LocalDateTime t2 = LocalDateTime.now().withNano(0);

        int id1 = dao.insert(conn, buildSale(insertedEventId, 1, "10.00", t1, null));
        insertedSaleIds.add(id1);
        int id2 = dao.insert(conn, buildSale(insertedEventId, 2, "20.00", t2, null));
        insertedSaleIds.add(id2);

        List<Sale> sales = dao.findByEventId(conn, insertedEventId);
        List<Sale> testSales = sales.stream()
                .filter(s -> s.getSaleId() == id1 || s.getSaleId() == id2)
                .toList();

        assertEquals(2, testSales.size(), "findByEventId should return all sales for the event");
        // Verify ordering: most recent first (DESC)
        assertTrue(testSales.get(0).getSaleTimestamp()
                        .isAfter(testSales.get(1).getSaleTimestamp())
                        || testSales.get(0).getSaleTimestamp()
                                .equals(testSales.get(1).getSaleTimestamp()),
                "Results should be ordered by sale_timestamp DESC");
    }

    // -------------------------------------------------------------------------
    // findByVendor
    // -------------------------------------------------------------------------

    /**
     * findByVendor() should return only the specified vendor's sales on the given date.
     */
    @Test
    void findByVendor_returnsOnlyVendorSalesOnDate() throws SQLException {
        LocalDateTime today = LocalDateTime.now().withNano(0);
        LocalDateTime yesterday = today.minusDays(1);

        // Sales for vendor 1 — today
        int id1 = dao.insert(conn, buildSale(insertedEventId, 1, "15.00", today, "Booth-1"));
        insertedSaleIds.add(id1);
        // Sale for vendor 2 — today (should NOT appear)
        int id2 = dao.insert(conn, buildSale(insertedEventId, 2, "25.00", today, "Booth-2"));
        insertedSaleIds.add(id2);
        // Sale for vendor 1 — yesterday (should NOT appear)
        int id3 = dao.insert(conn, buildSale(insertedEventId, 1, "35.00", yesterday, "Booth-1"));
        insertedSaleIds.add(id3);

        List<Sale> sales = dao.findByVendor(conn, 1, LocalDate.now());
        List<Sale> testSales = sales.stream()
                .filter(s -> s.getSaleId() == id1 || s.getSaleId() == id2 || s.getSaleId() == id3)
                .toList();

        assertEquals(1, testSales.size(), "findByVendor should return only vendor 1 sales for today");
        assertEquals(id1, testSales.get(0).getSaleId(), "Only the today/vendor1 sale should match");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int insertTestEvent(Connection conn, String namePrefix) throws SQLException {
        Event event = new Event();
        event.setName(namePrefix + "_event");
        event.setEventDate(LocalDateTime.now().plusDays(30));
        event.setActive(true);
        return eventDao.insert(conn, event);
    }

    private int insertTestZone(Connection conn, int eventId, String namePrefix) throws SQLException {
        Zone zone = new Zone();
        zone.setEventId(eventId);
        zone.setName(namePrefix + "_zone");
        zone.setPrice(new BigDecimal("50.00"));
        return zoneDao.insert(conn, zone);
    }

    private int insertTestSeat(Connection conn, int zoneId, String rowNumber, String seatNumber)
            throws SQLException {
        Seat seat = new Seat();
        seat.setZoneId(zoneId);
        seat.setRowNumber(rowNumber);
        seat.setSeatNumber(seatNumber);
        seat.setStatus(SeatStatus.AVAILABLE);
        return seatDao.insert(conn, seat);
    }

    private Sale buildSale(int eventId, int vendorId, String amount,
            LocalDateTime timestamp, String boothId) {
        Sale sale = new Sale();
        sale.setEventId(eventId);
        sale.setVendorId(vendorId);
        sale.setTotalAmount(new BigDecimal(amount));
        sale.setSaleTimestamp(timestamp);
        sale.setBoothId(boothId);
        return sale;
    }

    private SaleItem buildSaleItem(int seatId, String price) {
        SaleItem item = new SaleItem();
        item.setSeatId(seatId);
        item.setPricePaid(new BigDecimal(price));
        return item;
    }

    /** Deletes a sale by id (cascades sale_items via ON DELETE CASCADE). */
    private void deleteSale(Connection conn, int saleId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sales WHERE sale_id = ?")) {
            ps.setInt(1, saleId);
            ps.executeUpdate();
        }
    }

    /** Counts sale_items rows for a given sale_id. */
    private int countSaleItems(Connection conn, int saleId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM sale_items WHERE sale_id = ?")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
