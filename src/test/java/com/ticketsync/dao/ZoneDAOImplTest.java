package com.ticketsync.dao;

import com.ticketsync.model.Event;
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
 * Integration tests for {@link ZoneDAOImpl}.
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
 * <p>Each test inserts its own rows using a time-stamped name prefix and
 * cleans them up in {@link #tearDown()} to avoid collisions with existing data.
 */
class ZoneDAOImplTest {

    private ZoneDAOImpl dao;
    private EventDAOImpl eventDao;
    private Connection conn;

    /** Unique, short prefix avoids collisions while fitting DB column limits. */
    private final String prefix = "t" + (System.currentTimeMillis() % 10000);

    /** Tracks all zone_ids inserted during a test so tearDown can clean up. */
    private final List<Integer> insertedZoneIds = new ArrayList<>();

    /** Parent event owned by this test class; cleaned up in {@link #tearDown()}. */
    private int insertedEventId = -1;

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");
        dao = new ZoneDAOImpl();
        eventDao = new EventDAOImpl();
        conn = DatabaseConfig.getConnection();
        conn.setAutoCommit(true);
        // Create a parent event that all zone tests can reference
        insertedEventId = insertTestEvent(conn, prefix);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null) {
            try {
                for (int id : insertedZoneIds) {
                    if (id > 0) {
                        dao.delete(conn, id);
                    }
                }
                if (insertedEventId > 0) {
                    eventDao.delete(conn, insertedEventId);
                }
            } finally {
                conn.close();
            }
        }
        insertedZoneIds.clear();
    }

    // -------------------------------------------------------------------------
    // insert
    // -------------------------------------------------------------------------

    /**
     * insert() should return a positive generated zone_id and the zone must be
     * retrievable via findById() immediately after.
     */
    @Test
    void insert_returnsPositiveIdAndZoneIsRetrievable() throws SQLException {
        Zone zone = buildZone(insertedEventId, prefix + "_insert", new BigDecimal("50.00"));

        int id = dao.insert(conn, zone);
        insertedZoneIds.add(id);

        assertTrue(id > 0, "Generated zone_id should be positive");

        Optional<Zone> found = dao.findById(conn, id);
        assertTrue(found.isPresent(), "Inserted zone should be retrievable by id");
        assertEquals(prefix + "_insert", found.get().getName());
        assertEquals(0, new BigDecimal("50.00").compareTo(found.get().getPrice()));
        assertEquals(insertedEventId, found.get().getEventId());
    }

    /**
     * insert() should throw IllegalArgumentException for a null zone.
     */
    @Test
    void insert_nullZone_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.insert(conn, null));
    }

    // -------------------------------------------------------------------------
    // findByEventId
    // -------------------------------------------------------------------------

    /**
     * findByEventId() should return all zones for the given event, ordered by zone_id ASC.
     */
    @Test
    void findByEventId_returnsAllZonesForEvent() throws SQLException {
        Zone z1 = buildZone(insertedEventId, prefix + "_z1", new BigDecimal("30.00"));
        Zone z2 = buildZone(insertedEventId, prefix + "_z2", new BigDecimal("60.00"));

        int id1 = dao.insert(conn, z1);
        insertedZoneIds.add(id1);
        int id2 = dao.insert(conn, z2);
        insertedZoneIds.add(id2);

        List<Zone> zones = dao.findByEventId(conn, insertedEventId);

        assertNotNull(zones, "findByEventId should never return null");
        assertTrue(zones.stream().anyMatch(z -> z.getName().equals(prefix + "_z1")),
                "Result should contain first inserted zone");
        assertTrue(zones.stream().anyMatch(z -> z.getName().equals(prefix + "_z2")),
                "Result should contain second inserted zone");

        // Verify ORDER BY zone_id ASC: id1 must appear before id2
        int idx1 = -1, idx2 = -1;
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).getZoneId() == id1) idx1 = i;
            if (zones.get(i).getZoneId() == id2) idx2 = i;
        }
        assertTrue(idx1 < idx2, "findByEventId should be ordered by zone_id ASC");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    /**
     * update() should persist changes to name and price.
     */
    @Test
    void update_changesNameAndPrice() throws SQLException {
        Zone zone = buildZone(insertedEventId, prefix + "_upd", new BigDecimal("40.00"));
        int id = dao.insert(conn, zone);
        insertedZoneIds.add(id);

        Optional<Zone> inserted = dao.findById(conn, id);
        assertTrue(inserted.isPresent());

        Zone toUpdate = inserted.get();
        toUpdate.setName(prefix + "_upd_changed");
        toUpdate.setPrice(new BigDecimal("99.99"));
        dao.update(conn, toUpdate);

        Optional<Zone> updated = dao.findById(conn, id);
        assertTrue(updated.isPresent());
        assertEquals(prefix + "_upd_changed", updated.get().getName());
        assertEquals(0, new BigDecimal("99.99").compareTo(updated.get().getPrice()));
    }

    /**
     * update() should throw SQLException when no row matches the given zoneId.
     */
    @Test
    void update_nonExistentZone_throwsSQLException() {
        Zone ghost = buildZone(insertedEventId, prefix + "_ghost", new BigDecimal("10.00"));
        ghost.setZoneId(Integer.MAX_VALUE);
        assertThrows(SQLException.class,
                () -> dao.update(conn, ghost));
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    /**
     * After delete(), findById() for the same id should return Optional.empty().
     */
    @Test
    void delete_removesZone() throws SQLException {
        Zone zone = buildZone(insertedEventId, prefix + "_del", new BigDecimal("20.00"));
        int id = dao.insert(conn, zone);
        insertedZoneIds.add(id);

        dao.delete(conn, id);

        Optional<Zone> found = dao.findById(conn, id);
        assertFalse(found.isPresent(), "Deleted zone should not be found");

        // Mark already cleaned up so tearDown does not try again
        insertedZoneIds.set(insertedZoneIds.indexOf(id), -1);
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
     * Builds a {@link Zone} ready for insertion.
     */
    private Zone buildZone(int eventId, String name, BigDecimal price) {
        Zone zone = new Zone();
        zone.setEventId(eventId);
        zone.setName(name);
        zone.setPrice(price);
        return zone;
    }
}
