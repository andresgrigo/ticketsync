package com.ticketsync.service;

import com.ticketsync.dao.SeatDAO;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SeatService}.
 *
 * <p>No database connection is required — both the {@link SeatDAO} and the
 * {@link ConnectionFactory} are replaced with lightweight stubs.
 */
class SeatServiceTest {

    private static final User ADMIN_USER  = new User(1, "admin", "hash", "ADMIN",  null);
    private static final User VENDOR_USER = new User(2, "vendor", "hash", "VENDOR", null);

    private StubSeatDAO stubDao;
    private SeatService service;
    private Connection  noopConn;

    @BeforeEach
    void setUp() {
        stubDao  = new StubSeatDAO();
        noopConn = noopConnection();
        service  = new SeatService(stubDao, () -> noopConn);
        SessionContext.setCurrentUser(ADMIN_USER);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    // -----------------------------------------------------------------------
    // generateSeats — role checks
    // -----------------------------------------------------------------------

    @Test
    void generateSeats_noAdminRole_throwsSecurityException() {
        SessionContext.clearCurrentUser();
        assertThrows(SecurityException.class,
                () -> service.generateSeats(1, "A", 1, 5));
        assertFalse(stubDao.insertCalled, "insert() must NOT be called on security failure");
    }

    @Test
    void generateSeats_vendorRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        assertThrows(SecurityException.class,
                () -> service.generateSeats(1, "A", 1, 5));
        assertFalse(stubDao.insertCalled);
    }

    // -----------------------------------------------------------------------
    // generateSeats — validation
    // -----------------------------------------------------------------------

    @Test
    void generateSeats_invalidZoneId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSeats(0, "A", 1, 5));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void generateSeats_negativeZoneId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSeats(-1, "A", 1, 5));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void generateSeats_nullRowNumber_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSeats(1, null, 1, 5));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void generateSeats_blankRowNumber_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSeats(1, "  ", 1, 5));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void generateSeats_fromSeatLessThanOne_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSeats(1, "A", 0, 5));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void generateSeats_toSeatLessThanFromSeat_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSeats(1, "A", 5, 4));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void generateSeats_rangeExceedsMax_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generateSeats(1, "A", 1, 1001));
        assertFalse(stubDao.insertCalled);
    }

    @Test
    void generateSeats_rangeAtMax_succeeds() throws SQLException {
        service.generateSeats(1, "A", 1, 1000);
        assertEquals(1000, stubDao.insertedSeats.size());
    }

    // -----------------------------------------------------------------------
    // generateSeats — happy path
    // -----------------------------------------------------------------------

    @Test
    void generateSeats_validParams_insertsCorrectNumberOfSeats() throws SQLException {
        service.generateSeats(1, "A", 1, 5);

        assertTrue(stubDao.insertCalled, "insert() must be called for valid params");
        assertEquals(5, stubDao.insertedSeats.size(), "exactly 5 seats should be inserted");
    }

    @Test
    void generateSeats_validParams_allSeatsHaveCorrectZoneAndRow() throws SQLException {
        service.generateSeats(3, "B", 2, 4);

        for (Seat s : stubDao.insertedSeats) {
            assertEquals(3, s.getZoneId());
            assertEquals("B", s.getRowNumber());
            assertEquals(SeatStatus.AVAILABLE, s.getStatus());
        }
        assertEquals(3, stubDao.insertedSeats.size());
    }

    @Test
    void generateSeats_validParams_seatNumbersAreSequential() throws SQLException {
        service.generateSeats(1, "A", 3, 5);

        List<String> numbers = stubDao.insertedSeats.stream()
                .map(Seat::getSeatNumber)
                .toList();
        assertEquals(List.of("3", "4", "5"), numbers);
    }

    @Test
    void generateSeats_singleSeat_insertsOneSeat() throws SQLException {
        service.generateSeats(1, "Z", 7, 7);

        assertEquals(1, stubDao.insertedSeats.size());
        assertEquals("7", stubDao.insertedSeats.get(0).getSeatNumber());
    }

    // -----------------------------------------------------------------------
    // generateSeats — transaction rollback on failure
    // -----------------------------------------------------------------------

    @Test
    void generateSeats_daoThrowsSqlException_rollsBackAndRethrows() {
        stubDao.throwOnInsert = true;

        assertThrows(SQLException.class,
                () -> service.generateSeats(1, "A", 1, 3));
        assertTrue(stubDao.rollbackCalled, "rollback() must be called on SQLException");
    }

    // -----------------------------------------------------------------------
    // deleteSeatsTransaction — role checks and edge cases
    // -----------------------------------------------------------------------

    @Test
    void deleteSeatsTransaction_noAdminRole_throwsSecurityException() {
        SessionContext.clearCurrentUser();
        assertThrows(SecurityException.class,
                () -> service.deleteSeatsTransaction(List.of(1, 2)));
        assertFalse(stubDao.deleteCalled);
    }

    @Test
    void deleteSeatsTransaction_vendorRole_throwsSecurityException() {
        SessionContext.setCurrentUser(VENDOR_USER);
        assertThrows(SecurityException.class,
                () -> service.deleteSeatsTransaction(List.of(1, 2)));
        assertFalse(stubDao.deleteCalled);
    }

    @Test
    void deleteSeatsTransaction_nullList_doesNothing() throws SQLException {
        service.deleteSeatsTransaction(null);
        assertFalse(stubDao.deleteCalled);
    }

    @Test
    void deleteSeatsTransaction_emptyList_doesNothing() throws SQLException {
        service.deleteSeatsTransaction(Collections.emptyList());
        assertFalse(stubDao.deleteCalled);
    }

    // -----------------------------------------------------------------------
    // deleteSeatsTransaction — happy path
    // -----------------------------------------------------------------------

    @Test
    void deleteSeatsTransaction_validIds_deletesAllSeats() throws SQLException {
        service.deleteSeatsTransaction(List.of(10, 20, 30));

        assertTrue(stubDao.deleteCalled, "delete() must be called for valid ids");
        assertEquals(List.of(10, 20, 30), stubDao.deletedIds);
    }

    // -----------------------------------------------------------------------
    // deleteSeatsTransaction — transaction rollback on failure
    // -----------------------------------------------------------------------

    @Test
    void deleteSeatsTransaction_daoThrowsSqlException_rollsBackAndRethrows() {
        stubDao.throwOnDelete = true;

        assertThrows(SQLException.class,
                () -> service.deleteSeatsTransaction(List.of(1)));
        assertTrue(stubDao.rollbackCalled, "rollback() must be called on SQLException");
    }

    // -----------------------------------------------------------------------
    // getSeatsByZone
    // -----------------------------------------------------------------------

    @Test
    void getSeatsByZone_returnsSeatsList() throws SQLException {
        Seat s1 = seat(1, 5, "A", "1");
        Seat s2 = seat(2, 5, "A", "2");
        stubDao.seatsToReturn = List.of(s1, s2);

        List<Seat> result = service.getSeatsByZone(5);

        assertEquals(2, result.size());
        assertTrue(stubDao.findByZoneIdCalled);
        assertEquals(5, stubDao.lastFindByZoneIdArg);
    }

    @Test
    void getSeatsByZone_noSeats_returnsEmptyList() throws SQLException {
        stubDao.seatsToReturn = Collections.emptyList();

        List<Seat> result = service.getSeatsByZone(7);

        assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Seat seat(int seatId, int zoneId, String row, String number) {
        return new Seat(seatId, zoneId, row, number, SeatStatus.AVAILABLE, null);
    }

    /**
     * Creates a JDK dynamic-proxy {@link Connection} that silently accepts
     * transaction-related calls (setAutoCommit, commit, rollback, close)
     * and records rollback invocations via the stub DAO.
     */
    private Connection noopConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) return false;
                    if ("rollback".equals(method.getName())) stubDao.rollbackCalled = true;
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });
    }

    // -----------------------------------------------------------------------
    // Stub DAO
    // -----------------------------------------------------------------------

    static class StubSeatDAO implements SeatDAO {

        // --- configuration ---
        boolean      throwOnInsert = false;
        boolean      throwOnDelete = false;
        List<Seat>   seatsToReturn = Collections.emptyList();

        // --- invocation tracking ---
        boolean      insertCalled        = false;
        boolean      deleteCalled        = false;
        boolean      rollbackCalled      = false;
        boolean      findByZoneIdCalled  = false;
        int          lastFindByZoneIdArg = -1;
        List<Seat>   insertedSeats       = new ArrayList<>();
        List<Integer> deletedIds         = new ArrayList<>();

        @Override
        public Optional<Seat> findById(Connection conn, int seatId) throws SQLException {
            return Optional.empty();
        }

        @Override
        public List<Seat> findByZoneId(Connection conn, int zoneId) throws SQLException {
            findByZoneIdCalled = true;
            lastFindByZoneIdArg = zoneId;
            return seatsToReturn;
        }

        @Override
        public List<Seat> findByEventId(Connection conn, int eventId) throws SQLException {
            return Collections.emptyList();
        }

        @Override
        public int insert(Connection conn, Seat seat) throws SQLException {
            if (throwOnInsert) throw new SQLException("duplicate key value violates unique constraint", "23505");
            insertCalled = true;
            insertedSeats.add(seat);
            return insertedSeats.size();
        }

        @Override
        public void delete(Connection conn, int seatId) throws SQLException {
            if (throwOnDelete) throw new SQLException("delete failed");
            deleteCalled = true;
            deletedIds.add(seatId);
        }

        @Override
        public List<Seat> selectForUpdate(Connection conn, List<Integer> seatIds) throws SQLException {
            return Collections.emptyList();
        }

        @Override
        public void updateStatus(Connection conn, List<Integer> seatIds, SeatStatus status, Integer saleId)
                throws SQLException {
        }
    }
}
