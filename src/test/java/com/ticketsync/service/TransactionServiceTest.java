package com.ticketsync.service;

import com.ticketsync.dao.SaleDAO;
import com.ticketsync.dao.SeatDAO;
import com.ticketsync.exception.SeatUnavailableException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TransactionService}.
 *
 * <p>No database connection is required — both DAOs and the
 * {@link ConnectionFactory} are replaced with lightweight stubs.
 */
class TransactionServiceTest {

    private static final User VENDOR = new User(5, "vendor1", "hash", "VENDOR", null);
    private static final String BOOTH_ID = "Booth 5";

    private StubSeatDAO stubSeatDAO;
    private StubSaleDAO stubSaleDAO;
    private CapturingAuditService stubAuditService;
    private Connection noopConn;
    private TransactionService service;

    @BeforeEach
    void setUp() throws Exception {
        stubSeatDAO = new StubSeatDAO();
        stubSaleDAO = new StubSaleDAO();
        stubAuditService = new CapturingAuditService();
        noopConn = noopConnection();
        service = new TransactionService(stubSeatDAO, stubSaleDAO, stubAuditService, () -> noopConn);
        SessionContext.setCurrentUser(VENDOR);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — happy path
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_allSeatsAvailable_returnsSaleWithId() throws Exception {
        stubSeatDAO.seatsToReturn = List.of(
                availableSeat(10), availableSeat(11));
        stubSaleDAO.generatedSaleId = 42;

        Sale result = service.purchaseSeats(1, List.of(10, 11), new BigDecimal("20.00"), BOOTH_ID);

        assertEquals(42, result.getSaleId());
        assertEquals(1,  result.getEventId());
        assertEquals(5,  result.getVendorId());
        assertEquals(new BigDecimal("20.00"), result.getTotalAmount());
        assertNotNull(result.getSaleTimestamp());
        assertEquals(BOOTH_ID, result.getBoothId());
        assertNotNull(stubSaleDAO.insertedSale);
        assertEquals(BOOTH_ID, stubSaleDAO.insertedSale.getBoothId());
        assertTrue(stubSeatDAO.updateStatusCalled, "updateStatus must be called to mark seats SOLD");
        assertEquals(SeatStatus.SOLD, stubSeatDAO.lastUpdateStatusStatus, "seats must be marked SOLD");
    }

    @Test
    void purchaseSeats_allSeatsAvailable_commitsTransaction() throws Exception {
        stubSeatDAO.seatsToReturn = List.of(availableSeat(1));
        stubSaleDAO.generatedSaleId = 1;

        service.purchaseSeats(1, List.of(1), new BigDecimal("10.00"), BOOTH_ID);

        assertTrue(stubSeatDAO.commitCalled, "commit() must be called on successful purchase");
        assertFalse(stubSeatDAO.rollbackCalled, "rollback() must NOT be called on success");
    }

    @Test
    void purchaseSeats_success_writesPurchaseAuditAfterCommit() throws Exception {
        stubSeatDAO.seatsToReturn = List.of(availableSeat(10), availableSeat(11));
        stubSaleDAO.generatedSaleId = 77;

        service.purchaseSeats(1, List.of(10, 11), new BigDecimal("20.00"), BOOTH_ID);

        assertEquals(1, stubAuditService.persisted.size(), "purchase success must write one audit log");
        assertTrue(stubAuditService.commitSeenAtPersist, "purchase audit must be written only after commit");
        assertEquals("PURCHASE_SEATS", stubAuditService.persisted.getFirst().getAction());
        assertEquals("SALE", stubAuditService.persisted.getFirst().getEntityType());
        assertEquals(77, stubAuditService.persisted.getFirst().getEntityId());
        assertTrue(stubAuditService.persisted.getFirst().getDetails().contains("\"seatIds\":[10,11]"));
        assertTrue(stubAuditService.persisted.getFirst().getDetails().contains("\"boothId\":\"Booth 5\""));
    }

    @Test
    void purchaseSeats_auditFailure_doesNotMaskCommittedSale() throws Exception {
        stubSeatDAO.seatsToReturn = List.of(availableSeat(10), availableSeat(11));
        stubSaleDAO.generatedSaleId = 101;
        stubAuditService.throwOnPersist = true;

        Sale result = service.purchaseSeats(1, List.of(10, 11), new BigDecimal("20.00"), BOOTH_ID);

        assertEquals(101, result.getSaleId());
        assertTrue(stubSeatDAO.commitCalled, "business transaction must still commit");
        assertFalse(stubSeatDAO.rollbackCalled, "audit failure after commit must not roll back purchase");
    }

    @Test
    void purchaseSeats_seatItemsPriceDistributedEvenly() throws Exception {
        stubSeatDAO.seatsToReturn = List.of(availableSeat(1), availableSeat(2));
        stubSaleDAO.generatedSaleId = 7;

        service.purchaseSeats(2, List.of(1, 2), new BigDecimal("10.00"), BOOTH_ID);

        assertEquals(2, stubSaleDAO.insertedItems.size());
        assertEquals(new BigDecimal("5.00"), stubSaleDAO.insertedItems.get(0).getPricePaid());
        assertEquals(new BigDecimal("5.00"), stubSaleDAO.insertedItems.get(1).getPricePaid());
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — seat unavailable
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_seatNotAvailable_throwsSeatUnavailable() {
        stubSeatDAO.seatsToReturn = List.of(soldSeat(10));

        SeatUnavailableException ex = assertThrows(SeatUnavailableException.class,
                () -> service.purchaseSeats(1, List.of(10), new BigDecimal("10.00")));

        assertTrue(ex.getUnavailableSeatIds().contains(10));
        assertTrue(stubSeatDAO.rollbackCalled, "rollback() must be called when seat unavailable");
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — input validation
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_nullSeatIds_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(1, null, new BigDecimal("10.00")));
    }

    @Test
    void purchaseSeats_emptySeatIds_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(1, Collections.emptyList(), new BigDecimal("10.00")));
    }

    @Test
    void purchaseSeats_nullTotal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(1, List.of(1), null));
    }

    @Test
    void purchaseSeats_negativeTotal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(1, List.of(1), new BigDecimal("-1.00")));
    }

    @Test
    void purchaseSeats_zeroTotal_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(1, List.of(1), BigDecimal.ZERO));
    }

    @Test
    void purchaseSeats_zeroEventId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(0, List.of(1), new BigDecimal("10.00")));
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — session guard
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_noSessionUser_throwsIllegalState() {
        SessionContext.clearCurrentUser();

        assertThrows(IllegalStateException.class,
                () -> service.purchaseSeats(1, List.of(1), new BigDecimal("10.00")));
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — SQLException handling
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_sqlException_throwsSeatUnavailable() {
        stubSeatDAO.throwOnSelectForUpdate = true;

        SeatUnavailableException ex = assertThrows(SeatUnavailableException.class,
                () -> service.purchaseSeats(1, List.of(1, 2), new BigDecimal("20.00")));

        assertNotNull(ex.getCause(), "original SQLException must be preserved as cause");
        assertTrue(ex.getUnavailableSeatIds().containsAll(List.of(1, 2)));
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — rollback on SeatUnavailableException
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_seatUnavailable_rollbackCalled() {
        stubSeatDAO.seatsToReturn = List.of(soldSeat(5));

        assertThrows(SeatUnavailableException.class,
                () -> service.purchaseSeats(1, List.of(5), new BigDecimal("10.00")));

        assertTrue(stubSeatDAO.rollbackCalled);
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — transaction setup verification
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_transactionIsolationAndTimeoutConfigured() throws Exception {
        stubSeatDAO.seatsToReturn = List.of(availableSeat(1));
        stubSaleDAO.generatedSaleId = 1;

        service.purchaseSeats(1, List.of(1), new BigDecimal("10.00"), BOOTH_ID);

        assertTrue(stubSeatDAO.transactionIsolationSet, "setTransactionIsolation must be called");
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, stubSeatDAO.lastTransactionIsolation,
                "isolation level must be SERIALIZABLE");
        assertTrue(stubSeatDAO.autoCommitDisabled, "autoCommit must be disabled");
        assertTrue(stubSeatDAO.timeoutSet, "statement timeout must be set via SET LOCAL statement_timeout");
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — duplicate and null element validation
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_duplicateSeatIds_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(1, List.of(1, 1), new BigDecimal("10.00")));
    }

    @Test
    void purchaseSeats_nullElementInSeatIds_throwsIllegalArgument() {
        List<Integer> withNull = new ArrayList<>();
        withNull.add(1);
        withNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.purchaseSeats(1, withNull, new BigDecimal("10.00")));
    }

    // -----------------------------------------------------------------------
    // purchaseSeats — non-existent seat IDs
    // -----------------------------------------------------------------------

    @Test
    void purchaseSeats_seatNotFound_throwsSeatUnavailable() {
        // selectForUpdate returns fewer seats than requested (simulates seat not found)
        stubSeatDAO.seatsToReturn = List.of(availableSeat(1)); // only seat 1 found, seat 99 missing

        SeatUnavailableException ex = assertThrows(SeatUnavailableException.class,
                () -> service.purchaseSeats(1, List.of(1, 99), new BigDecimal("20.00")));

        assertTrue(ex.getUnavailableSeatIds().contains(99), "missing seat ID must be in exception");
        assertTrue(stubSeatDAO.rollbackCalled, "rollback() must be called when seat not found");
    }

    @Test
    void purchaseReceiptDetails_fromSale_buildsDeterministicReceiptMetadata() {
        Sale sale = new Sale(
                42,
                1,
                5,
                new BigDecimal("20.00"),
                java.time.LocalDateTime.of(2026, 4, 11, 14, 15, 9),
                BOOTH_ID
        );

        PurchaseReceiptDetails receipt = PurchaseReceiptDetails.fromSale(
                sale,
                List.of("Section A, Row 12, Seat 5", "Section A, Row 12, Seat 6")
        );

        assertEquals("TXN-20260411-141509-B5", receipt.transactionId());
        assertEquals("April 11, 2026 14:15:09", receipt.timestampText());
        assertEquals(BOOTH_ID, receipt.boothId());
        assertEquals("EUR20.00", receipt.totalPriceText());
        assertEquals(
                List.of("Section A, Row 12, Seat 5", "Section A, Row 12, Seat 6"),
                receipt.seatLines()
        );
    }

    @Test
    void purchaseReceiptDetails_formatTransactionId_nullBoothIdProducesBUNKNOWN() {
        Sale sale = new Sale(
                1,
                1,
                5,
                new BigDecimal("10.00"),
                java.time.LocalDateTime.of(2026, 4, 11, 10, 0, 0),
                null
        );

        String txnId = PurchaseReceiptDetails.formatTransactionId(sale);

        assertEquals("TXN-20260411-100000-BUNKNOWN", txnId);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Seat availableSeat(int seatId) {
        return new Seat(seatId, 1, "A", String.valueOf(seatId), SeatStatus.AVAILABLE, null);
    }

    private static Seat soldSeat(int seatId) {
        return new Seat(seatId, 1, "A", String.valueOf(seatId), SeatStatus.SOLD, null);
    }

    /**
     * Creates a JDK dynamic-proxy {@link Connection} that silently accepts
     * transaction-related calls (setTransactionIsolation, setAutoCommit, commit,
     * rollback, close) and records all invocations via the seat DAO stub.
     * Also handles createStatement() by returning a stub that tracks timeout execution.
     */
    private Connection noopConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) return false;
                    if ("rollback".equals(method.getName())) stubSeatDAO.rollbackCalled = true;
                    if ("commit".equals(method.getName())) stubSeatDAO.commitCalled = true;
                    if ("setTransactionIsolation".equals(method.getName())) {
                        int level = (Integer) args[0];
                        if (level > stubSeatDAO.lastTransactionIsolation)
                            stubSeatDAO.lastTransactionIsolation = level;
                        stubSeatDAO.transactionIsolationSet = true;
                    }
                    if ("setAutoCommit".equals(method.getName())) {
                        if (Boolean.FALSE.equals(args[0])) stubSeatDAO.autoCommitDisabled = true;
                    }
                    if ("createStatement".equals(method.getName())) {
                        return (Statement) Proxy.newProxyInstance(
                                Statement.class.getClassLoader(),
                                new Class<?>[] { Statement.class },
                                (p2, m2, a2) -> {
                                    if ("close".equals(m2.getName())) return null;
                                    if ("execute".equals(m2.getName())) {
                                        if (a2 != null && a2.length > 0 && a2[0] instanceof String) {
                                            if (((String) a2[0]).contains("statement_timeout"))
                                                stubSeatDAO.timeoutSet = true;
                                        }
                                        return false;
                                    }
                                    Class<?> ret2 = m2.getReturnType();
                                    if (ret2 == boolean.class) return false;
                                    if (ret2 == int.class)     return 0;
                                    return null;
                                });
                    }
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class)     return 0;
                    return null;
                });
    }

    // -----------------------------------------------------------------------
    // Stub DAOs
    // -----------------------------------------------------------------------

    static class StubSeatDAO implements SeatDAO {

        boolean throwOnSelectForUpdate = false;
        boolean rollbackCalled = false;
        boolean commitCalled = false;
        boolean transactionIsolationSet = false;
        int lastTransactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
        boolean autoCommitDisabled = false;
        boolean timeoutSet = false;
        List<Seat> seatsToReturn = Collections.emptyList();

        boolean updateStatusCalled = false;
        List<Integer> lastUpdateStatusIds = new ArrayList<>();
        SeatStatus lastUpdateStatusStatus = null;

        @Override
        public Optional<Seat> findById(Connection conn, int seatId) throws SQLException {
            return Optional.empty();
        }

        @Override
        public List<Seat> findByZoneId(Connection conn, int zoneId) throws SQLException {
            return Collections.emptyList();
        }

        @Override
        public List<Seat> findByEventId(Connection conn, int eventId) throws SQLException {
            return Collections.emptyList();
        }

        @Override
        public int insert(Connection conn, Seat seat) throws SQLException {
            return 0;
        }

        @Override
        public void delete(Connection conn, int seatId) throws SQLException {
        }

        @Override
        public List<Seat> selectForUpdate(Connection conn, List<Integer> seatIds) throws SQLException {
            if (throwOnSelectForUpdate) throw new SQLException("serialization failure", "40001");
            return seatsToReturn;
        }

        @Override
        public void updateStatus(Connection conn, List<Integer> seatIds, SeatStatus status, Integer saleId)
                throws SQLException {
            updateStatusCalled = true;
            lastUpdateStatusIds = new ArrayList<>(seatIds);
            lastUpdateStatusStatus = status;
        }
    }

    static class StubSaleDAO implements SaleDAO {

        int generatedSaleId = 1;
        List<SaleItem> insertedItems = new ArrayList<>();
        Sale insertedSale;

        @Override
        public Optional<Sale> findById(Connection conn, int saleId) throws SQLException {
            return Optional.empty();
        }

        @Override
        public List<SaleItem> findSaleItemsBySaleId(Connection conn, int saleId) throws SQLException {
            return Collections.emptyList();
        }

        @Override
        public List<Sale> findByEventId(Connection conn, int eventId) throws SQLException {
            return Collections.emptyList();
        }

        @Override
        public List<Sale> findByVendor(Connection conn, int vendorId, LocalDate date) throws SQLException {
            return Collections.emptyList();
        }

        @Override
        public int insert(Connection conn, Sale sale) throws SQLException {
            insertedSale = sale;
            return generatedSaleId;
        }

        @Override
        public void insertSaleItems(Connection conn, int saleId, List<SaleItem> items) throws SQLException {
            insertedItems.addAll(items);
        }
    }

    final class CapturingAuditService extends AuditService {
        final List<com.ticketsync.model.AuditLog> persisted = new ArrayList<>();
        boolean throwOnPersist;
        boolean commitSeenAtPersist;

        @Override
        protected void persistAuditLog(com.ticketsync.model.AuditLog auditLog) throws SQLException {
            commitSeenAtPersist = stubSeatDAO.commitCalled;
            if (throwOnPersist) {
                throw new SQLException("audit insert failed");
            }
            persisted.add(auditLog);
        }

        @Override
        protected List<com.ticketsync.model.AuditLog> queryAuditLogs(
                java.time.LocalDateTime fromInclusive,
                java.time.LocalDateTime toExclusive,
                String actionFilter,
                int limit) {
            return List.of();
        }
    }
}
