package com.ticketsync.controller;

import com.ticketsync.exception.SeatUnavailableException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PosPurchaseCoordinatorTest {

    @Test
    void execute_successBuildsReceiptDetailsAndRefreshesPurchasedSeats() throws Exception {
        StubSeatRefresher seatRefresher = new StubSeatRefresher();
        PosPurchaseCoordinator coordinator = new PosPurchaseCoordinator(
                (eventId, seatIds, totalAmount, boothId) -> new Sale(
                        42,
                        eventId,
                        5,
                        totalAmount,
                        LocalDateTime.of(2026, 4, 11, 14, 15, 9),
                        boothId
                ),
                seatRefresher,
                saleId -> Optional.empty()
        );

        PosPurchaseCoordinator.PurchaseOutcome outcome = coordinator.execute(new PosPurchaseCoordinator.PurchaseRequest(
                99,
                "Booth 5",
                new BigDecimal("79.49"),
                List.of(
                        new PosPurchaseCoordinator.SelectedSeat(1, 10, "Floor", "A", "1"),
                        new PosPurchaseCoordinator.SelectedSeat(3, 20, "Balcony", "B", "1")
                )
        ));

        PosPurchaseCoordinator.PurchaseSuccess success =
                assertInstanceOf(PosPurchaseCoordinator.PurchaseSuccess.class, outcome);
        assertEquals(List.of(1, 3), seatRefresher.refreshedSeatIds);
        assertEquals("TXN-20260411-141509-B5", success.receiptDetails().transactionId());
        assertEquals("April 11, 2026 14:15:09", success.receiptDetails().timestampText());
        assertEquals("Booth 5", success.receiptDetails().boothId());
        assertEquals("EUR79.49", success.receiptDetails().totalPriceText());
        assertEquals(
                List.of("Floor, Row A, Seat 1", "Balcony, Row B, Seat 1"),
                success.receiptDetails().seatLines()
        );
    }

    @Test
    void execute_conflictRefreshesSeatAndBuildsFriendlyRecoveryDetails() throws Exception {
        StubSeatRefresher seatRefresher = new StubSeatRefresher();
        seatRefresher.refreshedSeats = List.of(new Seat(1, 10, "A", "12", SeatStatus.SOLD, 88));
        PosPurchaseCoordinator coordinator = new PosPurchaseCoordinator(
                (eventId, seatIds, totalAmount, boothId) -> {
                    throw new SeatUnavailableException("Seat(s) unavailable: [1]", List.of(1));
                },
                seatRefresher,
                saleId -> Optional.of(new Sale(
                        saleId,
                        99,
                        3,
                        new BigDecimal("49.99"),
                        LocalDateTime.of(2026, 4, 11, 14, 32, 15),
                        "Booth 3"
                ))
        );

        PosPurchaseCoordinator.PurchaseOutcome outcome = coordinator.execute(new PosPurchaseCoordinator.PurchaseRequest(
                99,
                "Booth 5",
                new BigDecimal("49.99"),
                List.of(new PosPurchaseCoordinator.SelectedSeat(1, 10, "Section A", "12", "5"))
        ));

        PosPurchaseCoordinator.PurchaseConflict conflict =
                assertInstanceOf(PosPurchaseCoordinator.PurchaseConflict.class, outcome);
        assertEquals(List.of(1), seatRefresher.refreshedSeatIds);
        assertEquals("Show Available Seats in Section A", conflict.primaryActionLabel());
        assertEquals(
                "Seat Section A-12-5 sold by Booth 3 at April 11, 2026 14:32:15. Please select alternative seats.",
                conflict.message()
        );
        assertEquals(10, conflict.zoneId());
        assertEquals("Section A", conflict.zoneName());
    }

    private static final class StubSeatRefresher implements PosPurchaseCoordinator.SeatRefresher {
        private final List<Integer> refreshedSeatIds = new ArrayList<>();
        private List<Seat> refreshedSeats = List.of();

        @Override
        public List<Seat> refreshSeats(List<Integer> seatIds) throws SQLException {
            refreshedSeatIds.addAll(seatIds);
            return refreshedSeats;
        }
    }
}
