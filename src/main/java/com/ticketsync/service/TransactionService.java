package com.ticketsync.service;

import com.ticketsync.dao.SaleDAO;
import com.ticketsync.dao.SaleDAOImpl;
import com.ticketsync.dao.SeatDAO;
import com.ticketsync.dao.SeatDAOImpl;
import com.ticketsync.exception.SeatUnavailableException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service class for atomic seat-purchase transactions.
 *
 * <p>Each call to {@link #purchaseSeats} runs inside a single SERIALIZABLE
 * PostgreSQL transaction: seats are locked, availability is validated, a Sale
 * record and its SaleItem children are inserted, and the seats are marked SOLD
 * before the transaction commits. Any failure triggers a full rollback.
 *
 * <p>The service delegates all SQL to {@link SeatDAO} and {@link SaleDAO}.
 * No SQL strings live here.
 */
public class TransactionService {

    private static final Logger LOGGER = LogManager.getLogger(TransactionService.class);

    private final SeatDAO seatDAO;
    private final SaleDAO saleDAO;
    private final ConnectionFactory connFactory;

    /** Production constructor. */
    public TransactionService() {
        this.seatDAO = new SeatDAOImpl();
        this.saleDAO = new SaleDAOImpl();
        this.connFactory = DatabaseConfig::getConnection;
    }

    /** Test constructor — no DB required. */
    TransactionService(SeatDAO seatDAO, SaleDAO saleDAO, ConnectionFactory connFactory) {
        this.seatDAO = seatDAO;
        this.saleDAO = saleDAO;
        this.connFactory = connFactory;
    }

    /**
     * Executes an atomic seat-purchase transaction with SERIALIZABLE isolation.
     *
     * <p>Transaction flow:
     * <ol>
     *   <li>Obtain connection; set SERIALIZABLE + autoCommit=false + 10 s statement timeout</li>
     *   <li>Lock seats with SELECT FOR UPDATE</li>
     *   <li>Validate all seats are AVAILABLE</li>
     *   <li>Insert Sale record</li>
     *   <li>Insert SaleItem records (one per seat)</li>
     *   <li>Update seats to SOLD status with sale reference</li>
     *   <li>COMMIT</li>
     * </ol>
     *
     * @param eventId event for which tickets are being sold; must be positive
     * @param seatIds seat IDs to purchase; must not be null or empty
     * @param total   total purchase price for all seats; must be positive
     * @return the committed {@link Sale} with its DB-generated saleId set
     * @throws SeatUnavailableException if any seat is not AVAILABLE, or a serialization conflict occurs
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws IllegalStateException    if no user is logged in via {@link SessionContext}
     */
    public Sale purchaseSeats(int eventId, List<Integer> seatIds, BigDecimal total)
            throws SeatUnavailableException {
        return purchaseSeats(eventId, seatIds, total, null);
    }

    public Sale purchaseSeats(int eventId, List<Integer> seatIds, BigDecimal total, String boothId)
            throws SeatUnavailableException {
        if (eventId <= 0) throw new IllegalArgumentException("eventId must be positive");
        if (seatIds == null || seatIds.isEmpty()) throw new IllegalArgumentException("seatIds must not be null or empty");
        if (seatIds.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("seatIds must not contain null elements");
        if (new HashSet<>(seatIds).size() != seatIds.size()) throw new IllegalArgumentException("seatIds must not contain duplicates");
        if (total == null) throw new IllegalArgumentException("total must not be null");
        if (total.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("total must be positive");

        int vendorId = SessionContext.getCurrentUser()
                .map(User::getUserId)
                .orElseThrow(() -> new IllegalStateException("No user is logged in — cannot process purchase"));

        LOGGER.info("Processing purchase: event={}, seats={}, total={}, vendor={}",
                eventId, seatIds.size(), total, vendorId);

        Connection conn = null;
        try {
            conn = connFactory.get();
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL statement_timeout = '10000'");
            }

            // Step 1: Lock seats
            List<Seat> lockedSeats = seatDAO.selectForUpdate(conn, seatIds);

            // Guard: all requested seat IDs must have been found and locked
            if (lockedSeats.size() != seatIds.size()) {
                List<Integer> lockedIds = lockedSeats.stream().map(Seat::getSeatId).collect(Collectors.toList());
                List<Integer> notFound = seatIds.stream().filter(id -> !lockedIds.contains(id)).collect(Collectors.toList());
                throw new SeatUnavailableException("Seat(s) not found: " + notFound, notFound);
            }

            // Step 2: Validate availability
            List<Integer> unavailable = lockedSeats.stream()
                    .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                    .map(Seat::getSeatId)
                    .collect(Collectors.toList());
            if (!unavailable.isEmpty()) {
                throw new SeatUnavailableException(
                        "Seat(s) unavailable: " + unavailable, unavailable);
            }

            // Step 3: Build Sale
            Sale sale = new Sale();
            sale.setEventId(eventId);
            sale.setVendorId(vendorId);
            sale.setTotalAmount(total);
            sale.setSaleTimestamp(LocalDateTime.now());
            sale.setBoothId(normalizeBoothId(boothId));

            // Step 4: Insert Sale
            int saleId = saleDAO.insert(conn, sale);
            sale.setSaleId(saleId);

            // Step 5: Build and insert SaleItems
            BigDecimal pricePerSeat = total.divide(BigDecimal.valueOf(seatIds.size()), 2, RoundingMode.HALF_UP);
            List<SaleItem> items = new ArrayList<>();
            for (int sid : seatIds) {
                SaleItem item = new SaleItem();
                item.setSeatId(sid);
                item.setPricePaid(pricePerSeat);
                items.add(item);
            }
            saleDAO.insertSaleItems(conn, saleId, items);

            // Step 6: Mark seats SOLD
            seatDAO.updateStatus(conn, seatIds, SeatStatus.SOLD, saleId);

            conn.commit();
            LOGGER.info("Purchase committed: saleId={}, event={}, seats={}, vendor={}",
                    saleId, eventId, seatIds.size(), vendorId);
            return sale;

        } catch (SeatUnavailableException e) {
            rollbackQuietly(conn, eventId);
            throw e;
        } catch (RuntimeException e) {
            rollbackQuietly(conn, eventId);
            LOGGER.error("Unexpected error during purchase for event={}: {}", eventId, e.getMessage(), e);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(conn, eventId);
            LOGGER.error("Transaction failed for event={}: {}", eventId, e.getMessage(), e);
            throw new SeatUnavailableException(
                    "Purchase failed: concurrent modification", seatIds, e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void rollbackQuietly(Connection conn, int eventId) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                LOGGER.warn("Rollback failed for event={}: {}", eventId, ex.getMessage());
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                LOGGER.warn("Failed to reset autoCommit: {}", ex.getMessage());
            }
            try {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            } catch (SQLException ex) {
                LOGGER.warn("Failed to reset isolation level: {}", ex.getMessage());
            }
            try {
                conn.close();
            } catch (SQLException ex) {
                LOGGER.warn("Failed to close connection: {}", ex.getMessage());
            }
        }
    }

    private static String normalizeBoothId(String boothId) {
        if (boothId == null || boothId.isBlank()) {
            return null;
        }
        return boothId.strip();
    }
}
