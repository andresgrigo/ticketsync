package com.ticketsync.dao;

import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import com.ticketsync.util.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link SeatDAOImpl}.
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
 * <p>Each test inserts its own rows using a time-stamped seat number prefix and
 * cleans them up in {@link #tearDown()} to avoid collisions with existing data.
 */
class SeatDAOImplTest {

    private SeatDAOImpl dao;
    private ZoneDAOImpl zoneDao;
    private EventDAOImpl eventDao;
    private Connection conn;

    /** Unique prefix avoids collisions between test runs and parallel suites. */
    private final String prefix = "test_seat_" + System.currentTimeMillis();

    /** Tracks all seat_ids inserted during a test so tearDown can clean up. */
    private final List<Integer> insertedSeatIds = new ArrayList<>();

    /** Parent zone for this test; cleaned up (and cascades seats) in {@link #tearDown()}. */
    private int insertedZoneId = -1;

    /** Parent event for this test; cleaned up in {@link #tearDown()}. */
    private int insertedEventId = -1;

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");
        dao = new SeatDAOImpl();
        zoneDao = new ZoneDAOImpl();
        eventDao = new EventDAOImpl();
        conn = DatabaseConfig.getConnection();
        conn.setAutoCommit(true);
        // Create parent event and zone that seat tests can reference
        insertedEventId = insertTestEvent(conn, prefix);
        insertedZoneId = insertTestZone(conn, insertedEventId, prefix);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null) {
            try {
                // Delete remaining seats (zone cascade would also handle, but be explicit)
                for (int id : insertedSeatIds) {
                    if (id > 0) {
                        dao.delete(conn, id);
                    }
                }
                // Delete zone (cascades any remaining seats)
                if (insertedZoneId > 0) {
                    zoneDao.delete(conn, insertedZoneId);
                }
                // Delete event (cascades zones)
                if (insertedEventId > 0) {
                    eventDao.delete(conn, insertedEventId);
                }
            } finally {
                conn.close();
            }
        }
        insertedSeatIds.clear();
    }

    // -------------------------------------------------------------------------
    // insert
    // -------------------------------------------------------------------------

    /**
     * insert() should return a positive generated seat_id and the seat must be
     * retrievable via findById() immediately after.
     */
    @Test
    void insert_returnsPositiveIdAndSeatIsRetrievable() throws SQLException {
        Seat seat = buildSeat(insertedZoneId, prefix + "_A", "1");

        int id = dao.insert(conn, seat);
        insertedSeatIds.add(id);

        assertTrue(id > 0, "Generated seat_id should be positive");

        Optional<Seat> found = dao.findById(conn, id);
        assertTrue(found.isPresent(), "Inserted seat should be retrievable by id");
        assertEquals(prefix + "_A", found.get().getRowNumber());
        assertEquals("1", found.get().getSeatNumber());
        assertEquals(SeatStatus.AVAILABLE, found.get().getStatus());
        assertNull(found.get().getSaleId(), "New seat should have null saleId");
    }

    /**
     * insert() should throw IllegalArgumentException for a null seat.
     */
    @Test
    void insert_nullSeat_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.insert(conn, null));
    }

    // -------------------------------------------------------------------------
    // findByZoneId
    // -------------------------------------------------------------------------

    /**
     * findByZoneId() should return seats ordered by row_number ASC, seat_number ASC.
     */
    @Test
    void findByZoneId_returnsSeatsOrderedByRowAndNumber() throws SQLException {
        // Insert seats in reverse order to verify sorting
        int id3 = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_B", "2"));
        insertedSeatIds.add(id3);
        int id2 = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_B", "1"));
        insertedSeatIds.add(id2);
        int id1 = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_A", "1"));
        insertedSeatIds.add(id1);

        List<Seat> seats = dao.findByZoneId(conn, insertedZoneId);

        // Filter to only our test seats
        List<Seat> testSeats = seats.stream()
                .filter(s -> s.getRowNumber().startsWith(prefix))
                .collect(java.util.stream.Collectors.toList());

        assertEquals(3, testSeats.size(), "Should find exactly 3 inserted seats");
        // Row prefix_A should come before prefix_B alphabetically
        assertTrue(testSeats.get(0).getRowNumber().equals(prefix + "_A"),
                "First seat should be row prefix_A (sorts alphabetically first)");
        assertEquals("1", testSeats.get(1).getSeatNumber(),
                "Within row prefix_B, seat 1 should precede seat 2");
        assertEquals("2", testSeats.get(2).getSeatNumber());
    }

    // -------------------------------------------------------------------------
    // findByEventId
    // -------------------------------------------------------------------------

    /**
     * findByEventId() should return all seats across multiple zones for the event.
     */
    @Test
    void findByEventId_returnsSeatsAcrossAllZones() throws SQLException {
        // Add a second zone to the same event
        int zone2Id = insertTestZone(conn, insertedEventId, prefix + "_z2");

        int sid1 = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_R", "1"));
        insertedSeatIds.add(sid1);
        int sid2 = dao.insert(conn, buildSeat(zone2Id, prefix + "_R", "1"));
        insertedSeatIds.add(sid2);

        List<Seat> seats = dao.findByEventId(conn, insertedEventId);

        List<Seat> testSeats = seats.stream()
                .filter(s -> s.getRowNumber().startsWith(prefix))
                .collect(java.util.stream.Collectors.toList());

        assertEquals(2, testSeats.size(), "Should find seats from both zones");
        assertTrue(testSeats.stream().anyMatch(s -> s.getZoneId() == insertedZoneId));
        assertTrue(testSeats.stream().anyMatch(s -> s.getZoneId() == zone2Id));

        // Clean up zone2; seats cascade-deleted with zone
        // Wrapped in try/finally so cleanup always runs even if assertions above threw
        try {
            zoneDao.delete(conn, zone2Id);
        } finally {
            // Those seats are now gone — prevent tearDown from double-deleting
            insertedSeatIds.remove(Integer.valueOf(sid1));
            insertedSeatIds.remove(Integer.valueOf(sid2));
        }
    }

    // -------------------------------------------------------------------------
    // updateStatus
    // -------------------------------------------------------------------------

    /**
     * updateStatus() with DISABLED status should set status to DISABLED and sale_id to NULL.
     */
    @Test
    void updateStatus_availableToDisabled_setsStatusAndNullSaleId() throws SQLException {
        int id = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_D", "1"));
        insertedSeatIds.add(id);

        dao.updateStatus(conn, List.of(id), SeatStatus.DISABLED, null);

        Optional<Seat> found = dao.findById(conn, id);
        assertTrue(found.isPresent());
        assertEquals(SeatStatus.DISABLED, found.get().getStatus());
        assertNull(found.get().getSaleId(), "sale_id should be NULL for non-SOLD status");
    }

    // -------------------------------------------------------------------------
    // selectForUpdate
    // -------------------------------------------------------------------------

    /**
     * selectForUpdate() under a SERIALIZABLE transaction should return the locked seats
     * with correct field values (happy-path, AC6).
     */
    @Test
    void selectForUpdate_serializable_returnsLockedSeats() throws SQLException {
        int id = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_SFUH", "1"));
        insertedSeatIds.add(id);

        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        try {
            List<Seat> locked = dao.selectForUpdate(conn, List.of(id));

            assertEquals(1, locked.size(), "Should return the one requested seat");
            assertEquals(id, locked.get(0).getSeatId());
            assertEquals(insertedZoneId, locked.get(0).getZoneId());
            assertEquals(prefix + "_SFUH", locked.get(0).getRowNumber());
            assertEquals("1", locked.get(0).getSeatNumber());
            assertEquals(SeatStatus.AVAILABLE, locked.get(0).getStatus());
            assertNull(locked.get(0).getSaleId());
        } finally {
            conn.rollback();
            conn.setAutoCommit(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    /**
     * selectForUpdate() should throw IllegalStateException when connection isolation
     * level is not SERIALIZABLE.
     */
    @Test
    void selectForUpdate_nonSerializableIsolation_throwsIllegalState() throws SQLException {
        int id = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_SFU", "1"));
        insertedSeatIds.add(id);

        // conn has autocommit=true; isolation defaults to READ_COMMITTED, not SERIALIZABLE
        assertThrows(IllegalStateException.class,
                () -> dao.selectForUpdate(conn, List.of(id)));
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    /**
     * After delete(), findById() for the same id should return Optional.empty().
     */
    @Test
    void delete_removesSeat() throws SQLException {
        int id = dao.insert(conn, buildSeat(insertedZoneId, prefix + "_DEL", "1"));
        insertedSeatIds.add(id);

        dao.delete(conn, id);

        Optional<Seat> found = dao.findById(conn, id);
        assertFalse(found.isPresent(), "Deleted seat should not be found");

        // Mark already cleaned up so tearDown does not try again
        insertedSeatIds.remove(Integer.valueOf(id));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Inserts a minimal test event via EventDAOImpl and returns the generated event_id.
     */
    private int insertTestEvent(Connection conn, String namePrefix) throws SQLException {
        Event event = new Event();
        event.setName(namePrefix + "_event");
        event.setEventDate(LocalDateTime.now().plusDays(30));
        event.setActive(true);
        return eventDao.insert(conn, event);
    }

    /**
     * Inserts a minimal test zone via ZoneDAOImpl and returns the generated zone_id.
     */
    private int insertTestZone(Connection conn, int eventId, String namePrefix) throws SQLException {
        Zone zone = new Zone();
        zone.setEventId(eventId);
        zone.setName(namePrefix + "_zone");
        zone.setPrice(new BigDecimal("50.00"));
        return zoneDao.insert(conn, zone);
    }

    /**
     * Builds a {@link Seat} with AVAILABLE status, ready for insertion.
     *
     * @param zoneId    parent zone
     * @param rowNumber row identifier
     * @param seatNumber seat identifier within the row
     */
    private Seat buildSeat(int zoneId, String rowNumber, String seatNumber) {
        Seat seat = new Seat();
        seat.setZoneId(zoneId);
        seat.setRowNumber(rowNumber);
        seat.setSeatNumber(seatNumber);
        seat.setStatus(SeatStatus.AVAILABLE);
        return seat;
    }
}
