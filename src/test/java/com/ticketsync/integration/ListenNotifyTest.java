package com.ticketsync.integration;

import com.ticketsync.service.SeatSyncService;
import com.ticketsync.util.DatabaseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for PostgreSQL LISTEN/NOTIFY via {@link SeatSyncService}.
 *
 * <p><strong>Prerequisites:</strong> PostgreSQL running on localhost:5432,
 * database {@code ticketsync} exists, Flyway migrations V001-V005 applied,
 * and at least one seat row exists in the {@code seats} table.
 *
 * <p><strong>Enable test:</strong>
 * <pre>
 * # PowerShell
 * $env:TICKETSYNC_MASTER_KEY="your-key"; $env:DB_TEST_ENABLED="true"; mvn test
 * </pre>
 */
class ListenNotifyTest {

    @BeforeEach
    void setUp() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");
    }

    @Test
    void seatStatusUpdate_triggersListenNotifyCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger receivedSeatId = new AtomicInteger(-1);

        SeatSyncService service = new SeatSyncService();
        service.startListening(seatId -> {
            receivedSeatId.set(seatId);
            latch.countDown();
        });

        try (Connection conn = DatabaseConfig.getConnection()) {
            // Reset to AVAILABLE first so the subsequent SOLD update always changes status and fires the trigger
            try (PreparedStatement reset = conn.prepareStatement(
                    "UPDATE seats SET status = 'AVAILABLE' WHERE seat_id = (SELECT seat_id FROM seats LIMIT 1)")) {
                reset.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seats SET status = 'SOLD' WHERE seat_id = (SELECT seat_id FROM seats LIMIT 1)")) {
                ps.executeUpdate();
            }
        }

        boolean notified = latch.await(1, TimeUnit.SECONDS);
        service.stopListening();

        assertTrue(notified, "Callback must fire within 1 second of the UPDATE");
        assertTrue(receivedSeatId.get() > 0, "Received seat ID must be positive");
    }
}
