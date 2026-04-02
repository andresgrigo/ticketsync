package com.ticketsync.service;

import com.ticketsync.dao.EventDAO;
import com.ticketsync.model.Event;
import com.ticketsync.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EventService}.
 *
 * <p>No database connection is required — both the {@link EventDAO} and the
 * {@link ConnectionFactory} are replaced with lightweight stubs.  The
 * {@link Connection} stub is a JDK dynamic proxy that accepts {@code close()}
 * calls silently; the stub DAO records call arguments and returns pre-configured
 * values without touching any real database.
 */
class EventServiceTest {

    private static final User ADMIN_USER  = new User(42, "testadmin", "hash", "ADMIN",  null);
    private static final User VENDOR_USER = new User(99, "vendor1",   "hash", "VENDOR", null);

    private StubEventDAO        stubDao;
    private EventService        service;
    private Connection          noopConn;

    @BeforeEach
    void setUp() {
        stubDao  = new StubEventDAO();
        noopConn = noopConnection();
        service  = new EventService(stubDao, () -> noopConn);
        SessionContext.setCurrentUser(ADMIN_USER);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    // -----------------------------------------------------------------------
    // createEvent — role checks
    // -----------------------------------------------------------------------

    @Test
    void createEvent_noAdminRole_throwsSecurityException() {
        SessionContext.clearCurrentUser();
        assertThrows(SecurityException.class, () -> service.createEvent(futureEvent()));
    }

    @Test
    void createEvent_vendorRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        assertThrows(SecurityException.class, () -> service.createEvent(futureEvent()));
    }

    // -----------------------------------------------------------------------
    // createEvent — field validation (no DB write must occur)
    // -----------------------------------------------------------------------

    @Test
    void createEvent_nullName_throwsIllegalArgument() {
        Event e = futureEvent();
        e.setName(null);
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(e));
        assertFalse(stubDao.insertCalled, "insert() must NOT be called on validation failure");
    }

    @Test
    void createEvent_blankName_throwsIllegalArgument() {
        Event e = futureEvent();
        e.setName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(e));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void createEvent_nullVenue_throwsIllegalArgument() {
        Event e = futureEvent();
        e.setVenue(null);
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(e));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void createEvent_blankVenue_throwsIllegalArgument() {
        Event e = futureEvent();
        e.setVenue("   ");
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(e));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void createEvent_nullEventDate_throwsIllegalArgument() {
        Event e = futureEvent();
        e.setEventDate(null);
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(e));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void createEvent_pastEventDate_throwsIllegalArgument() {
        Event e = futureEvent();
        e.setEventDate(LocalDateTime.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.createEvent(e));
        assertFalse(stubDao.insertCalled);
    }

    // -----------------------------------------------------------------------
    // createEvent — happy path
    // -----------------------------------------------------------------------

    @Test
    void createEvent_validEvent_setsCreatedByAndCallsInsert() throws SQLException {
        stubDao.nextInsertId = 7;
        Event e = futureEvent();

        int id = service.createEvent(e);

        assertTrue(stubDao.insertCalled,  "insert() must be called for a valid event");
        assertEquals(42, e.getCreatedBy(), "createdBy must be set to the admin user id");
        assertEquals(7, id,                "returned id must match the generated event_id");
    }

    // -----------------------------------------------------------------------
    // updateEvent
    // -----------------------------------------------------------------------

    @Test
    void updateEvent_noAdminRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        Event e = futureEvent();
        e.setEventId(5);
        assertThrows(SecurityException.class, () -> service.updateEvent(e));
        assertFalse(stubDao.updateCalled);
    }

    @Test
    void updateEvent_invalidId_throwsIllegalArgument() {
        Event e = futureEvent();
        e.setEventId(0);
        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(e));
        assertFalse(stubDao.updateCalled);
    }

    @Test
    void updateEvent_eventNotFound_throwsIllegalArgument() {
        stubDao.findByIdReturnsEmpty = true;
        Event e = futureEvent();
        e.setEventId(5);
        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(e));
        assertFalse(stubDao.updateCalled);
    }

    @Test
    void updateEvent_validEvent_callsUpdate() throws SQLException {
        Event existing = futureEvent();
        existing.setEventId(5);
        stubDao.eventToReturn = existing;

        Event e = futureEvent();
        e.setEventId(5);
        service.updateEvent(e);

        assertTrue(stubDao.updateCalled);
    }

    // -----------------------------------------------------------------------
    // deleteEvent
    // -----------------------------------------------------------------------

    @Test
    void deleteEvent_noAdminRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        assertThrows(SecurityException.class, () -> service.deleteEvent(3));
        assertFalse(stubDao.deleteCalled);
    }

    @Test
    void deleteEvent_invalidId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteEvent(0));
        assertFalse(stubDao.deleteCalled);
    }

    @Test
    void deleteEvent_validId_callsDelete() throws SQLException {
        service.deleteEvent(3);

        assertTrue(stubDao.deleteCalled);
        assertEquals(3, stubDao.lastDeletedId);
    }

    // -----------------------------------------------------------------------
    // activateEvent
    // -----------------------------------------------------------------------

    @Test
    void activateEvent_noAdminRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        assertThrows(SecurityException.class, () -> service.activateEvent(10));
        assertFalse(stubDao.updateCalled);
    }

    @Test
    void activateEvent_eventNotFound_throwsIllegalArgument() {
        stubDao.findByIdReturnsEmpty = true;
        assertThrows(IllegalArgumentException.class, () -> service.activateEvent(99));
        assertFalse(stubDao.updateCalled);
    }

    @Test
    void activateEvent_validId_setsActiveTrueAndCallsUpdate() throws SQLException {
        Event existing = futureEvent();
        existing.setEventId(10);
        existing.setActive(false);
        stubDao.eventToReturn = existing;

        service.activateEvent(10);

        assertTrue(existing.isActive(), "event.isActive() must be true after activateEvent()");
        assertTrue(stubDao.updateCalled);
    }

    // -----------------------------------------------------------------------
    // deactivateEvent
    // -----------------------------------------------------------------------

    @Test
    void deactivateEvent_noAdminRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        assertThrows(SecurityException.class, () -> service.deactivateEvent(11));
        assertFalse(stubDao.updateCalled);
    }

    @Test
    void deactivateEvent_eventNotFound_throwsIllegalArgument() {
        stubDao.findByIdReturnsEmpty = true;
        assertThrows(IllegalArgumentException.class, () -> service.deactivateEvent(99));
        assertFalse(stubDao.updateCalled);
    }

    @Test
    void deactivateEvent_validId_setsActiveFalseAndCallsUpdate() throws SQLException {
        Event existing = futureEvent();
        existing.setEventId(11);
        existing.setActive(true);
        stubDao.eventToReturn = existing;

        service.deactivateEvent(11);

        assertFalse(existing.isActive(), "event.isActive() must be false after deactivateEvent()");
        assertTrue(stubDao.updateCalled);
    }

    // -----------------------------------------------------------------------
    // getActiveEvents
    // -----------------------------------------------------------------------

    @Test
    void getActiveEvents_unauthenticated_throwsSecurityException() {
        SessionContext.clearCurrentUser();
        assertThrows(SecurityException.class, () -> service.getActiveEvents());
    }

    @Test
    void getActiveEvents_vendorRole_returnsActiveList() throws SQLException {
        SessionContext.setCurrentUser(VENDOR_USER);   // VENDOR is authenticated — must succeed
        Event active = futureEvent();
        active.setActive(true);
        stubDao.activeEventsToReturn = List.of(active);

        List<Event> result = service.getActiveEvents();

        assertTrue(stubDao.findActiveCalled);
        assertEquals(1, result.size());
    }

    // -----------------------------------------------------------------------
    // findAllEvents
    // -----------------------------------------------------------------------

    @Test
    void findAllEvents_noAdminRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        assertThrows(SecurityException.class, () -> service.findAllEvents());
    }

    @Test
    void findAllEvents_adminRole_callsFindAll() throws SQLException {
        stubDao.allEventsToReturn = List.of(futureEvent());

        List<Event> result = service.findAllEvents();

        assertTrue(stubDao.findAllCalled);
        assertEquals(1, result.size());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns a fully-valid {@link Event} with a future {@code eventDate}. */
    private static Event futureEvent() {
        Event e = new Event();
        e.setName("Test Concert");
        e.setEventDate(LocalDateTime.now().plusDays(7));
        e.setVenue("Main Arena");
        return e;
    }

    /**
     * Creates a JDK dynamic-proxy {@link Connection} that silently accepts
     * {@code close()} (and any other void method) and returns {@code false} for
     * {@code isClosed()}.  The stub DAO never uses the connection object, so
     * no other methods are required.
     */
    private static Connection noopConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    // Return default value for return type (null / 0 / false)
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });
    }

    // -----------------------------------------------------------------------
    // Stub DAO
    // -----------------------------------------------------------------------

    /** Minimal stub {@link EventDAO} that tracks invocations without touching a DB. */
    static class StubEventDAO implements EventDAO {

        // --- configuration ---
        boolean        findByIdReturnsEmpty  = false;
        Event          eventToReturn         = null;
        int            nextInsertId          = 1;
        List<Event>    activeEventsToReturn  = Collections.emptyList();
        List<Event>    allEventsToReturn     = Collections.emptyList();

        // --- invocation flags ---
        boolean insertCalled     = false;
        boolean updateCalled     = false;
        boolean deleteCalled     = false;
        boolean findActiveCalled = false;
        boolean findAllCalled    = false;
        int     lastDeletedId    = -1;

        @Override
        public Optional<Event> findById(Connection conn, int eventId) throws SQLException {
            return findByIdReturnsEmpty ? Optional.empty() : Optional.ofNullable(eventToReturn);
        }

        @Override
        public List<Event> findAll(Connection conn) throws SQLException {
            findAllCalled = true;
            return allEventsToReturn;
        }

        @Override
        public List<Event> findActive(Connection conn) throws SQLException {
            findActiveCalled = true;
            return activeEventsToReturn;
        }

        @Override
        public int insert(Connection conn, Event event) throws SQLException {
            insertCalled = true;
            return nextInsertId;
        }

        @Override
        public void update(Connection conn, Event event) throws SQLException {
            updateCalled = true;
        }

        @Override
        public void delete(Connection conn, int eventId) throws SQLException {
            deleteCalled = true;
            lastDeletedId = eventId;
        }
    }
}
