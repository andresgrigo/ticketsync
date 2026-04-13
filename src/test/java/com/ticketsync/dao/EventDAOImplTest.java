package com.ticketsync.dao;

import com.ticketsync.model.Event;
import com.ticketsync.util.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link EventDAOImpl}.
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
class EventDAOImplTest {

    private EventDAOImpl dao;
    private Connection conn;

    /** Unique, short prefix avoids collisions while fitting DB column limits. */
    private final String prefix = "t" + (System.currentTimeMillis() % 10000);

    /**
     * Tracks all event_ids inserted during a test so tearDown can clean up.
     * Using a list to support tests that insert multiple events.
     */
    private final List<Integer> insertedEventIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");

        dao = new EventDAOImpl();
        conn = DatabaseConfig.getConnection();
        conn.setAutoCommit(true);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null) {
            try {
                for (int id : insertedEventIds) {
                    if (id > 0) {
                        dao.delete(conn, id);
                    }
                }
            } finally {
                conn.close();
            }
        }
        insertedEventIds.clear();
    }

    // -------------------------------------------------------------------------
    // insert
    // -------------------------------------------------------------------------

    /**
     * insert() should return a positive generated event_id and the event must be
     * retrievable via findById() immediately after.
     */
    @Test
    void insert_returnsPositiveIdAndEventIsRetrievable() throws SQLException {
        Event event = buildEvent(prefix + "_insert");

        int id = dao.insert(conn, event);
        insertedEventIds.add(id);

        assertTrue(id > 0, "Generated event_id should be positive");

        Optional<Event> found = dao.findById(conn, id);
        assertTrue(found.isPresent(), "Inserted event should be retrievable by id");
        assertEquals(prefix + "_insert", found.get().getName());
    }

    /**
     * insert() should throw IllegalArgumentException for a null event.
     */
    @Test
    void insert_nullEvent_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.insert(conn, null));
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    /**
     * findById() should return an event with all fields correctly mapped.
     */
    @Test
    void findById_existingEvent_returnsCorrectFields() throws SQLException {
        Event event = buildEvent(prefix + "_findById");
        event.setVenue("Test Venue");
        event.setDescription("Test Description");
        event.setActive(true);

        int id = dao.insert(conn, event);
        insertedEventIds.add(id);

        Optional<Event> found = dao.findById(conn, id);
        assertTrue(found.isPresent());
        assertEquals(prefix + "_findById", found.get().getName());
        assertEquals("Test Venue", found.get().getVenue());
        assertEquals("Test Description", found.get().getDescription());
        assertTrue(found.get().isActive());
        assertNotNull(found.get().getEventDate());
        assertNotNull(found.get().getCreatedAt());
    }

    /**
     * findById() should return Optional.empty() for an unknown id.
     */
    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<Event> found = dao.findById(conn, Integer.MAX_VALUE);
        assertFalse(found.isPresent(), "Unknown id should return Optional.empty()");
    }

    /**
     * findById() should throw IllegalArgumentException for id &lt;= 0.
     */
    @Test
    void findById_invalidId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.findById(conn, 0));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findById(conn, -1));
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    /**
    * findAll() should return a non-null list that contains inserted events
    * ordered by event_date DESC.
     */
    @Test
    void findAll_containsInsertedEvent() throws SQLException {
        Event earlier = buildEvent(prefix + "_findAll_earlier");
        earlier.setEventDate(LocalDateTime.now().plusDays(10));
        int earlierId = dao.insert(conn, earlier);
        insertedEventIds.add(earlierId);

        Event later = buildEvent(prefix + "_findAll_later");
        later.setEventDate(LocalDateTime.now().plusDays(60));
        int laterId = dao.insert(conn, later);
        insertedEventIds.add(laterId);

        List<Event> all = dao.findAll(conn);
        assertNotNull(all, "findAll should never return null");
        assertTrue(all.stream().anyMatch(e -> e.getName().equals(prefix + "_findAll_earlier")),
                "findAll should contain the earlier event");
        assertTrue(all.stream().anyMatch(e -> e.getName().equals(prefix + "_findAll_later")),
                "findAll should contain the later event");

        // Verify ORDER BY event_date DESC: later-dated event must precede earlier-dated event
        int laterIdx = -1, earlierIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getName().equals(prefix + "_findAll_later"))   laterIdx  = i;
            if (all.get(i).getName().equals(prefix + "_findAll_earlier")) earlierIdx = i;
        }
        assertTrue(laterIdx < earlierIdx,
                "findAll should be ordered by event_date DESC (later event before earlier event)");
    }

    // -------------------------------------------------------------------------
    // findActive
    // -------------------------------------------------------------------------

    /**
    * findActive() should return only events where is_active = true, ordered by
    * event_date DESC. Inserts two active events with different dates and one
     * inactive event; asserts filter correctness and ordering.
     */
    @Test
    void findActive_returnsOnlyActiveEvents() throws SQLException {
        Event activeLater = buildEvent(prefix + "_active_later");
        activeLater.setActive(true);
        activeLater.setEventDate(LocalDateTime.now().plusDays(60));
        int activeLaterId = dao.insert(conn, activeLater);
        insertedEventIds.add(activeLaterId);

        Event activeEarlier = buildEvent(prefix + "_active_earlier");
        activeEarlier.setActive(true);
        activeEarlier.setEventDate(LocalDateTime.now().plusDays(10));
        int activeEarlierId = dao.insert(conn, activeEarlier);
        insertedEventIds.add(activeEarlierId);

        Event inactiveEvent = buildEvent(prefix + "_inactive");
        inactiveEvent.setActive(false);
        int inactiveId = dao.insert(conn, inactiveEvent);
        insertedEventIds.add(inactiveId);

        List<Event> active = dao.findActive(conn);
        assertNotNull(active, "findActive should never return null");

        assertTrue(active.stream().anyMatch(e -> e.getName().equals(prefix + "_active_later")),
                "findActive should contain the active later event");
        assertTrue(active.stream().anyMatch(e -> e.getName().equals(prefix + "_active_earlier")),
                "findActive should contain the active earlier event");
        assertFalse(active.stream().anyMatch(e -> e.getName().equals(prefix + "_inactive")),
                "findActive should NOT contain the inactive event");

        // Verify ORDER BY event_date DESC: later-dated event must precede earlier-dated event
        int laterIdx = -1, earlierIdx = -1;
        for (int i = 0; i < active.size(); i++) {
            if (active.get(i).getName().equals(prefix + "_active_later"))   laterIdx  = i;
            if (active.get(i).getName().equals(prefix + "_active_earlier")) earlierIdx = i;
        }
        assertTrue(laterIdx < earlierIdx,
                "findActive should be ordered by event_date DESC (later event before earlier event)");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    /**
     * update() should persist the toggled is_active flag; subsequent findById
     * returns the updated value.
     */
    @Test
    void update_isActiveFlagToggled() throws SQLException {
        Event event = buildEvent(prefix + "_update");
        event.setActive(true);
        int id = dao.insert(conn, event);
        insertedEventIds.add(id);

        Optional<Event> inserted = dao.findById(conn, id);
        assertTrue(inserted.isPresent());

        Event toUpdate = inserted.get();
        toUpdate.setActive(false);
        dao.update(conn, toUpdate);

        Optional<Event> updated = dao.findById(conn, id);
        assertTrue(updated.isPresent());
        assertFalse(updated.get().isActive(), "is_active should have been toggled to false");
    }

    /**
     * update() should throw IllegalArgumentException when event is null.
     */
    @Test
    void update_nullEvent_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.update(conn, null));
    }

    /**
     * update() should throw IllegalArgumentException when eventId &lt;= 0.
     */
    @Test
    void update_invalidEventId_throwsIllegalArgument() {
        Event event = buildEvent(prefix + "_invalid");
        event.setEventId(0);
        assertThrows(IllegalArgumentException.class,
                () -> dao.update(conn, event));

        event.setEventId(-1);
        assertThrows(IllegalArgumentException.class,
                () -> dao.update(conn, event));
    }

    /**
     * update() should throw SQLException when no row matches the given eventId
     * (e.g. event was deleted between load and update).
     */
    @Test
    void update_nonExistentEventId_throwsSQLException() {
        Event ghost = buildEvent(prefix + "_ghost");
        ghost.setEventId(Integer.MAX_VALUE);
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
    void delete_subsequentFindByIdReturnsEmpty() throws SQLException {
        Event event = buildEvent(prefix + "_delete");
        int id = dao.insert(conn, event);
        insertedEventIds.add(id);

        dao.delete(conn, id);
        Optional<Event> found = dao.findById(conn, id);
        assertFalse(found.isPresent(), "Deleted event should not be found");

        // Mark as already cleaned up so tearDown does not try again
        insertedEventIds.set(insertedEventIds.indexOf(id), -1);
    }

    /**
     * delete() should throw IllegalArgumentException for id &lt;= 0.
     */
    @Test
    void delete_invalidId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.delete(conn, 0));
        assertThrows(IllegalArgumentException.class,
                () -> dao.delete(conn, -5));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a test {@link Event} with the given name, a future date, and active=true.
     *
     * @param name unique event name for this test case
     * @return ready-to-insert Event
     */
    private Event buildEvent(String name) {
        Event event = new Event();
        event.setName(name);
        event.setEventDate(LocalDateTime.now().plusDays(30));
        event.setVenue(null);
        event.setDescription(null);
        event.setActive(true);
        // createdBy = 0 → NULL in DB (no FK to users required for testing)
        return event;
    }
}
