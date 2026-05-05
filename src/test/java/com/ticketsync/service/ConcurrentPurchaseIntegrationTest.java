package com.ticketsync.service;

import com.ticketsync.dao.AuditDAOImpl;
import com.ticketsync.dao.SaleDAOImpl;
import com.ticketsync.dao.SeatDAOImpl;
import com.ticketsync.exception.SeatUnavailableException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.User;
import com.ticketsync.util.PasswordHasher;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class ConcurrentPurchaseIntegrationTest {

    private static final int WORKER_COUNT = 10;
    private static final int REPETITION_COUNT = 100;
    private static final long CONNECTION_TIMEOUT_MILLIS = 10_000L;
    private static final BigDecimal SEAT_PRICE = new BigDecimal("45.00");
    private static final String VENDOR_USERNAME = "it_vendor_concurrency";
    private static final String VENDOR_PASSWORD = "VendorPass!123";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            ContainerBackedPostgresHarness.createPostgresContainer();

    private static Flyway flyway;
    private static HikariDataSource dataSource;
    private static ConnectionFactory connFactory;
    private static TransactionService transactionService;
    private static ExecutorService executorService;
    private static User vendorUser;

    @BeforeAll
    static void setUpClass() throws SQLException {
        flyway = ContainerBackedPostgresHarness.createFlyway(POSTGRES);
        flyway.migrate();

        dataSource = ContainerBackedPostgresHarness.createDataSource(
                POSTGRES,
                "TicketSync-ConcurrencyTest-Pool",
                15,
                5,
                CONNECTION_TIMEOUT_MILLIS
        );
        connFactory = dataSource::getConnection;

        AuditService auditService = new AuditService(new AuditDAOImpl(), connFactory);
        transactionService = new TransactionService(new SeatDAOImpl(), new SaleDAOImpl(), auditService, connFactory);
        executorService = Executors.newFixedThreadPool(WORKER_COUNT);

        truncateApplicationTables();
        deleteConcurrencyVendorFixtures();
        vendorUser = insertVendorFixture();
    }

    @BeforeEach
    void setUp() throws SQLException {
        SessionContext.clearCurrentUser();
        truncateApplicationTables();
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @AfterAll
    static void tearDownClass() throws Exception {
        SessionContext.clearCurrentUser();

        if (connFactory != null) {
            truncateApplicationTables();
            deleteConcurrencyVendorFixtures();
        }

        if (executorService != null) {
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS),
                    "Worker pool should stop cleanly after the contention test");
        }

        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void purchaseRace_allowsExactlyOneWinnerAndZeroOversellsAcross100Runs() throws Exception {
        for (int repetition = 1; repetition <= REPETITION_COUNT; repetition++) {
            truncateApplicationTables();
            ContentionFixture fixture = seedFixture(repetition);

            RaceOutcome outcome = runContentionRace(fixture, repetition);

            assertEquals(1, outcome.successes().size(),
                    "Repetition %d should produce exactly one committed winner".formatted(repetition));
            assertEquals(WORKER_COUNT - 1, outcome.seatUnavailableFailures().size(),
                    "Repetition %d should produce exactly nine SeatUnavailableException losers".formatted(repetition));
            assertTrue(outcome.unexpectedFailures().isEmpty(),
                    "Repetition %d should not leak unexpected failures: %s"
                            .formatted(repetition, outcome.unexpectedFailures()));

            WorkerSuccess winner = outcome.successes().get(0);
            assertTrue(winner.sale().getSaleId() > 0, "Winning sale should have a generated id");
            assertEquals(winner.boothId(), winner.sale().getBoothId(), "Winning sale should keep its booth id");

            for (WorkerSeatUnavailableFailure failure : outcome.seatUnavailableFailures()) {
                assertEquals(List.of(fixture.seatId()), failure.exception().getUnavailableSeatIds(),
                        "Loser outcomes should point at the contested seat only");
            }

            PersistedRaceState persistedState = loadPersistedRaceState(fixture);
            assertEquals(1, persistedState.saleCount(),
                    "Repetition %d should persist exactly one sales row".formatted(repetition));
            assertEquals(1, persistedState.saleItemCount(),
                    "Repetition %d should persist exactly one sale_items row".formatted(repetition));
            assertEquals("SOLD", persistedState.seatStatus(),
                    "Repetition %d should leave the contested seat SOLD".formatted(repetition));
            assertNotNull(persistedState.saleId(),
                    "Repetition %d should keep a winning sale reference on the seat".formatted(repetition));
            assertEquals(winner.sale().getSaleId(), persistedState.saleId(),
                    "Repetition %d should point the contested seat at the winning sale".formatted(repetition));
            assertEquals(winner.sale().getSaleId(), persistedState.persistedSaleId(),
                    "Repetition %d should persist the winner's sale row only".formatted(repetition));
            assertEquals(winner.boothId(), persistedState.boothId(),
                    "Repetition %d should persist the winning booth id".formatted(repetition));
        }
    }

    private static User insertVendorFixture() throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setString(1, VENDOR_USERNAME);
            ps.setString(2, PasswordHasher.hashPassword(VENDOR_PASSWORD));
            ps.setString(3, "VENDOR");
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "Vendor fixture should return a generated id");
                return new User(
                        keys.getInt(1),
                        VENDOR_USERNAME,
                        PasswordHasher.hashPassword(VENDOR_PASSWORD),
                        "VENDOR",
                        LocalDateTime.now()
                );
            }
        }
    }

    private static void deleteConcurrencyVendorFixtures() throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
            ps.setString(1, VENDOR_USERNAME);
            ps.executeUpdate();
        }
    }

    private static void truncateApplicationTables() throws SQLException {
        try (Connection conn = connFactory.get();
             Statement statement = conn.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE audit_log, sale_items, sales, seats, zones, events RESTART IDENTITY CASCADE
                    """);
        }
    }

    private ContentionFixture seedFixture(int repetition) throws SQLException {
        int eventId = insertEvent(repetition);
        int zoneId = insertZone(eventId);
        int seatId = insertSeat(zoneId, repetition);
        return new ContentionFixture(eventId, zoneId, seatId);
    }

    private int insertEvent(int repetition) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     INSERT INTO events (name, event_date, venue, description, is_active, created_by)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setString(1, "Concurrency Event %03d".formatted(repetition));
            ps.setObject(2, LocalDateTime.of(2026, 9, 1, 19, 30).plusDays(repetition));
            ps.setString(3, "Concurrency Arena");
            ps.setString(4, "Zero oversell contention proof");
            ps.setBoolean(5, true);
            ps.setInt(6, vendorUser.getUserId());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "Event fixture should return a generated id");
                return keys.getInt(1);
            }
        }
    }

    private int insertZone(int eventId) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO zones (event_id, name, price) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setInt(1, eventId);
            ps.setString(2, "Floor");
            ps.setBigDecimal(3, SEAT_PRICE);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "Zone fixture should return a generated id");
                return keys.getInt(1);
            }
        }
    }

    private int insertSeat(int zoneId, int repetition) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO seats (zone_id, row_number, seat_number, status) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setInt(1, zoneId);
            ps.setString(2, "A");
            ps.setString(3, "%03d".formatted(repetition));
            ps.setString(4, "AVAILABLE");
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "Seat fixture should return a generated id");
                return keys.getInt(1);
            }
        }
    }

    private RaceOutcome runContentionRace(ContentionFixture fixture, int repetition) throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(WORKER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(WORKER_COUNT);

        ConcurrentLinkedQueue<WorkerSuccess> successes = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<WorkerSeatUnavailableFailure> seatUnavailableFailures = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<WorkerUnexpectedFailure> unexpectedFailures = new ConcurrentLinkedQueue<>();

        for (int workerIndex = 1; workerIndex <= WORKER_COUNT; workerIndex++) {
            final int currentWorker = workerIndex;
            executorService.submit(() -> runWorker(
                    fixture,
                    repetition,
                    currentWorker,
                    readyLatch,
                    startLatch,
                    doneLatch,
                    successes,
                    seatUnavailableFailures,
                    unexpectedFailures
            ));
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS),
                "All workers should be ready before the contention race starts");
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS),
                "All workers should complete the contention race");

        return new RaceOutcome(
                List.copyOf(successes),
                List.copyOf(seatUnavailableFailures),
                List.copyOf(unexpectedFailures)
        );
    }

    private void runWorker(ContentionFixture fixture,
                           int repetition,
                           int workerIndex,
                           CountDownLatch readyLatch,
                           CountDownLatch startLatch,
                           CountDownLatch doneLatch,
                           ConcurrentLinkedQueue<WorkerSuccess> successes,
                           ConcurrentLinkedQueue<WorkerSeatUnavailableFailure> seatUnavailableFailures,
                           ConcurrentLinkedQueue<WorkerUnexpectedFailure> unexpectedFailures) {
        String boothId = "Booth-" + workerIndex;
        readyLatch.countDown();

        try {
            if (!startLatch.await(10, TimeUnit.SECONDS)) {
                unexpectedFailures.add(new WorkerUnexpectedFailure(
                        boothId,
                        new IllegalStateException("Start gate did not release in time")
                ));
                return;
            }

            applyDeterministicJitter(repetition, workerIndex);
            SessionContext.setCurrentUser(vendorUser);

            try {
                Sale sale = transactionService.purchaseSeats(
                        fixture.eventId(),
                        List.of(fixture.seatId()),
                        SEAT_PRICE,
                        boothId
                );
                successes.add(new WorkerSuccess(boothId, sale));
            } catch (SeatUnavailableException e) {
                if (hasUnexpectedSqlCause(e)) {
                    unexpectedFailures.add(new WorkerUnexpectedFailure(boothId, e));
                } else {
                    seatUnavailableFailures.add(new WorkerSeatUnavailableFailure(boothId, e));
                }
            } catch (Exception e) {
                unexpectedFailures.add(new WorkerUnexpectedFailure(boothId, e));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            unexpectedFailures.add(new WorkerUnexpectedFailure(boothId, e));
        } finally {
            SessionContext.clearCurrentUser();
            doneLatch.countDown();
        }
    }

    private void applyDeterministicJitter(int repetition, int workerIndex) throws InterruptedException {
        long jitterMillis = (repetition + (workerIndex * 3L)) % 5L;
        if (jitterMillis > 0) {
            TimeUnit.MILLISECONDS.sleep(jitterMillis);
        }
    }

    private boolean hasUnexpectedSqlCause(SeatUnavailableException exception) {
        if (!(exception.getCause() instanceof SQLException sqlException)) {
            return false;
        }

        return !"40001".equals(sqlException.getSQLState());
    }

    private PersistedRaceState loadPersistedRaceState(ContentionFixture fixture) throws SQLException {
        int saleCount = 0;
        Integer persistedSaleId = null;
        String boothId = null;

        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sale_id, booth_id FROM sales WHERE event_id = ? ORDER BY sale_id ASC"
             )) {
            ps.setInt(1, fixture.eventId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    saleCount++;
                    persistedSaleId = rs.getInt("sale_id");
                    boothId = rs.getString("booth_id");
                }
            }
        }

        int saleItemCount;
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM sale_items WHERE seat_id = ?"
             )) {
            ps.setInt(1, fixture.seatId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Sale-item count query should return a row");
                saleItemCount = rs.getInt(1);
            }
        }

        String seatStatus;
        Integer seatSaleId;
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status, sale_id FROM seats WHERE seat_id = ?"
             )) {
            ps.setInt(1, fixture.seatId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Contested seat should remain queryable");
                seatStatus = rs.getString("status");
                seatSaleId = (Integer) rs.getObject("sale_id");
                assertFalse(rs.next(), "Only one contested seat row should exist");
            }
        }

        return new PersistedRaceState(saleCount, persistedSaleId, boothId, saleItemCount, seatStatus, seatSaleId);
    }

    private record ContentionFixture(int eventId, int zoneId, int seatId) {
    }

    private record WorkerSuccess(String boothId, Sale sale) {
    }

    private record WorkerSeatUnavailableFailure(String boothId, SeatUnavailableException exception) {
    }

    private record WorkerUnexpectedFailure(String boothId, Throwable cause) {
    }

    private record RaceOutcome(List<WorkerSuccess> successes,
                               List<WorkerSeatUnavailableFailure> seatUnavailableFailures,
                               List<WorkerUnexpectedFailure> unexpectedFailures) {
    }

    private record PersistedRaceState(int saleCount,
                                      Integer persistedSaleId,
                                      String boothId,
                                      int saleItemCount,
                                      String seatStatus,
                                      Integer saleId) {
    }
}
