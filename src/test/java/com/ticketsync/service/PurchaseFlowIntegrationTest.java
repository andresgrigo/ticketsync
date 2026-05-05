package com.ticketsync.service;

import com.ticketsync.dao.AuditDAOImpl;
import com.ticketsync.dao.EventDAOImpl;
import com.ticketsync.dao.SaleDAOImpl;
import com.ticketsync.dao.SeatDAOImpl;
import com.ticketsync.dao.UserDAOImpl;
import com.ticketsync.dao.ZoneDAOImpl;
import com.ticketsync.model.Event;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.model.Seat;
import com.ticketsync.model.User;
import com.ticketsync.util.PasswordHasher;
import org.flywaydb.core.Flyway;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB-backed integration coverage for the complete vendor purchase flow.
 *
 * <p>The class intentionally lives in the {@code service} package so it can reuse the
 * package-private {@link ConnectionFactory} seam and injectable service constructors
 * without widening production visibility.
 */
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class PurchaseFlowIntegrationTest {

    private static final String PURCHASE_TOTAL = "90.00";
    private static final String BOOTH_ID = "Booth-7";
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            ContainerBackedPostgresHarness.createPostgresContainer();

    private static ConnectionFactory connFactory;
    private static Flyway flyway;
    private static UserDAOImpl userDAO;
    private static EventDAOImpl eventDAO;
    private static ZoneDAOImpl zoneDAO;
    private static SeatDAOImpl seatDAO;
    private static SaleDAOImpl saleDAO;
    private static AuthenticationService authenticationService;
    private static TransactionService transactionService;
    private static SaleLookupService saleLookupService;
    private static TicketGenerator ticketGenerator;

    private int vendorId;
    private String vendorUsername;
    private String vendorPassword;
    private int eventId;
    private int zoneId;
    private List<Integer> createdSeatIds;

    @BeforeAll
    static void setUpClass() {
        connFactory = ContainerBackedPostgresHarness.createConnectionFactory(POSTGRES);
        flyway = ContainerBackedPostgresHarness.createFlyway(POSTGRES);
        flyway.migrate();

        userDAO = new UserDAOImpl();
        eventDAO = new EventDAOImpl();
        zoneDAO = new ZoneDAOImpl();
        seatDAO = new SeatDAOImpl();
        saleDAO = new SaleDAOImpl();

        AuditService auditService = new AuditService(new AuditDAOImpl(), connFactory);
        authenticationService = new AuthenticationService(userDAO, auditService, connFactory);
        transactionService = new TransactionService(seatDAO, saleDAO, auditService, connFactory);
        saleLookupService = new SaleLookupService(saleDAO, connFactory);
        ticketGenerator = new TicketGenerator(eventDAO, seatDAO, zoneDAO, connFactory);
    }

    @BeforeEach
    void setUp() throws SQLException {
        SessionContext.clearCurrentUser();
        truncateApplicationTables();

        vendorUsername = "it_vendor_purchase";
        vendorPassword = "VendorPass!123";
        vendorId = insertVendor(vendorUsername, vendorPassword);
        eventId = insertEvent(vendorId);
        zoneId = insertZone(eventId, new BigDecimal("45.00"));
        createdSeatIds = insertAvailableSeats(zoneId, 10);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @Test
    void purchaseFlow_commitsSaleUpdatesSeatsWritesAuditAndGeneratesTicketPdf() throws Exception {
        Event event = loadEvent(eventId);
        List<Seat> availableSeats = loadSeatsForEvent(eventId);

        assertNotNull(event);
        assertTrue(event.isActive(), "Seeded event should be active for the vendor flow");
        assertEquals(10, availableSeats.size(), "Fixture should expose exactly ten seats");

        List<Integer> purchasedSeatIds = availableSeats.stream()
                .limit(2)
                .map(Seat::getSeatId)
                .toList();

        Optional<User> authenticatedVendor = authenticationService.login(vendorUsername, vendorPassword);
        assertTrue(authenticatedVendor.isPresent(), "Vendor login should succeed against the container-backed DB");
        assertEquals(vendorUsername, SessionContext.getCurrentUser().map(User::getUsername).orElseThrow());

        Sale committedSale = transactionService.purchaseSeats(
                event.getEventId(),
                purchasedSeatIds,
                new BigDecimal(PURCHASE_TOTAL),
                BOOTH_ID
        );

        assertTrue(committedSale.getSaleId() > 0, "Committed sale should have a generated id");
        assertEquals(new BigDecimal(PURCHASE_TOTAL), committedSale.getTotalAmount());
        assertEquals(BOOTH_ID, committedSale.getBoothId());

        PersistedSaleSnapshot saleSnapshot = loadSaleSnapshot(committedSale.getSaleId());
        assertEquals(new BigDecimal(PURCHASE_TOTAL), saleSnapshot.totalAmount());
        assertEquals(BOOTH_ID, saleSnapshot.boothId());

        List<PersistedSeatSnapshot> seatSnapshots = loadSeatSnapshots(purchasedSeatIds);
        assertEquals(2, seatSnapshots.size(), "Both purchased seats should remain queryable");
        for (PersistedSeatSnapshot seatSnapshot : seatSnapshots) {
            assertEquals("SOLD", seatSnapshot.status());
            assertEquals(committedSale.getSaleId(), seatSnapshot.saleId());
        }

        List<PersistedSaleItemSnapshot> saleItems = loadPersistedSaleItems(committedSale.getSaleId());
        assertEquals(2, saleItems.size(), "Two line items should be stored for the committed sale");
        assertEquals(
                purchasedSeatIds.stream().sorted().toList(),
                saleItems.stream().map(PersistedSaleItemSnapshot::seatId).sorted().toList()
        );
        BigDecimal persistedTotal = saleItems.stream()
                .map(PersistedSaleItemSnapshot::pricePaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal(PURCHASE_TOTAL), persistedTotal, "Persisted line items should sum to the purchase total");
        assertTrue(saleItems.stream().allMatch(item -> item.pricePaid().compareTo(new BigDecimal("45.00")) == 0));

        PurchaseAuditSnapshot purchaseAudit = loadPurchaseAudit(committedSale.getSaleId());
        assertEquals(BOOTH_ID, purchaseAudit.boothId());
        assertEquals(new BigDecimal(PURCHASE_TOTAL), purchaseAudit.total());
        assertEquals(purchasedSeatIds.stream().sorted().toList(), purchaseAudit.seatIds());

        Sale persistedSale = saleLookupService.getSaleById(committedSale.getSaleId()).orElseThrow();
        List<SaleItem> persistedItems = saleLookupService.getSaleItemsBySaleId(committedSale.getSaleId());
        assertEquals(2, persistedItems.size(), "Sale lookup should expose committed line items");

        byte[] pdfBytes = ticketGenerator.generateTicket(persistedSale, persistedItems);
        assertTrue(pdfBytes.length > 0, "Generated PDF ticket should not be empty");

        assertFalse(SessionContext.getCurrentUser().isEmpty(), "Session should remain populated until teardown");
    }

    private static void truncateApplicationTables() throws SQLException {
        try (Connection conn = connFactory.get();
             Statement statement = conn.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE audit_log, sale_items, sales, seats, zones, events RESTART IDENTITY CASCADE
                    """);
            statement.execute("DELETE FROM users WHERE username LIKE 'it_vendor_%'");
        }
    }

    private int insertVendor(String username, String password) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?) ",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setString(1, username);
            ps.setString(2, PasswordHasher.hashPassword(password));
            ps.setString(3, "VENDOR");
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "Vendor insert should return a generated id");
                return keys.getInt(1);
            }
        }
    }

    private int insertEvent(int createdBy) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     INSERT INTO events (name, event_date, venue, description, is_active, created_by)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setString(1, "Integration Purchase Test Event");
            ps.setObject(2, LocalDateTime.of(2026, 8, 12, 19, 30));
            ps.setString(3, "Integration Arena");
            ps.setString(4, "Happy-path purchase flow coverage");
            ps.setBoolean(5, true);
            ps.setInt(6, createdBy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "Event insert should return a generated id");
                return keys.getInt(1);
            }
        }
    }

    private int insertZone(int targetEventId, BigDecimal price) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO zones (event_id, name, price) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setInt(1, targetEventId);
            ps.setString(2, "Floor");
            ps.setBigDecimal(3, price);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next(), "Zone insert should return a generated id");
                return keys.getInt(1);
            }
        }
    }

    private List<Integer> insertAvailableSeats(int targetZoneId, int seatCount) throws SQLException {
        List<Integer> seatIds = new ArrayList<>();
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO seats (zone_id, row_number, seat_number, status) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS
             )) {
            for (int seatNumber = 1; seatNumber <= seatCount; seatNumber++) {
                ps.setInt(1, targetZoneId);
                ps.setString(2, "A");
                ps.setString(3, "%02d".formatted(seatNumber));
                ps.setString(4, "AVAILABLE");
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    assertTrue(keys.next(), "Seat insert should return a generated id");
                    seatIds.add(keys.getInt(1));
                }
            }
        }
        return seatIds;
    }

    private Event loadEvent(int targetEventId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return eventDAO.findById(conn, targetEventId).orElseThrow();
        }
    }

    private List<Seat> loadSeatsForEvent(int targetEventId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return seatDAO.findByEventId(conn, targetEventId).stream()
                    .sorted(Comparator.comparingInt(Seat::getSeatId))
                    .toList();
        }
    }

    private PersistedSaleSnapshot loadSaleSnapshot(int saleId) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT total_amount, booth_id FROM sales WHERE sale_id = ?"
             )) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Committed sale should exist in the database");
                return new PersistedSaleSnapshot(rs.getBigDecimal("total_amount"), rs.getString("booth_id"));
            }
        }
    }

    private List<PersistedSeatSnapshot> loadSeatSnapshots(List<Integer> seatIds) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT seat_id, status, sale_id
                     FROM seats
                     WHERE seat_id = ANY(?)
                     ORDER BY seat_id ASC
                     """
             )) {
            ps.setArray(1, conn.createArrayOf("INTEGER", seatIds.toArray(Integer[]::new)));
            try (ResultSet rs = ps.executeQuery()) {
                List<PersistedSeatSnapshot> snapshots = new ArrayList<>();
                while (rs.next()) {
                    snapshots.add(new PersistedSeatSnapshot(
                            rs.getInt("seat_id"),
                            rs.getString("status"),
                            rs.getInt("sale_id")
                    ));
                }
                return snapshots;
            }
        }
    }

    private List<PersistedSaleItemSnapshot> loadPersistedSaleItems(int saleId) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT seat_id, price_paid FROM sale_items WHERE sale_id = ? ORDER BY seat_id ASC"
             )) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PersistedSaleItemSnapshot> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(new PersistedSaleItemSnapshot(
                            rs.getInt("seat_id"),
                            rs.getBigDecimal("price_paid")
                    ));
                }
                return items;
            }
        }
    }

    private PurchaseAuditSnapshot loadPurchaseAudit(int saleId) throws SQLException {
        String boothId;
        BigDecimal total;

        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT details->>'boothId' AS booth_id, details->>'total' AS total
                     FROM audit_log
                     WHERE action = ? AND entity_id = ?
                     """
             )) {
            ps.setString(1, AuditService.Action.PURCHASE_SEATS.name());
            ps.setInt(2, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Purchase audit row should be persisted");
                boothId = rs.getString("booth_id");
                total = new BigDecimal(rs.getString("total"));
                assertFalse(rs.next(), "Only one purchase audit row should exist for the committed sale");
            }
        }

        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT seat_id
                     FROM (
                       SELECT jsonb_array_elements_text(details->'seatIds') AS seat_id
                       FROM audit_log
                       WHERE action = ? AND entity_id = ?
                     ) seat_ids
                     ORDER BY seat_id::int
                     """
             )) {
            ps.setString(1, AuditService.Action.PURCHASE_SEATS.name());
            ps.setInt(2, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> seatIds = new ArrayList<>();
                while (rs.next()) {
                    seatIds.add(Integer.parseInt(rs.getString("seat_id")));
                }
                return new PurchaseAuditSnapshot(boothId, total, seatIds);
            }
        }
    }

    private record PersistedSaleSnapshot(BigDecimal totalAmount, String boothId) {
    }

    private record PersistedSeatSnapshot(int seatId, String status, int saleId) {
    }

    private record PersistedSaleItemSnapshot(int seatId, BigDecimal pricePaid) {
    }

    private record PurchaseAuditSnapshot(String boothId, BigDecimal total, List<Integer> seatIds) {
    }

}
