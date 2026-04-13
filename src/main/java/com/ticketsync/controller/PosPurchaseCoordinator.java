package com.ticketsync.controller;

import com.ticketsync.exception.SeatUnavailableException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.Seat;
import com.ticketsync.service.PurchaseReceiptDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class PosPurchaseCoordinator {

    private static final Logger LOGGER = LogManager.getLogger(PosPurchaseCoordinator.class);

    private final PurchaseExecutor purchaseExecutor;
    private final SeatRefresher seatRefresher;
    private final SaleLookup saleLookup;

    PosPurchaseCoordinator(
            PurchaseExecutor purchaseExecutor,
            SeatRefresher seatRefresher,
            SaleLookup saleLookup
    ) {
        this.purchaseExecutor = Objects.requireNonNull(purchaseExecutor, "purchaseExecutor must not be null");
        this.seatRefresher = Objects.requireNonNull(seatRefresher, "seatRefresher must not be null");
        this.saleLookup = Objects.requireNonNull(saleLookup, "saleLookup must not be null");
    }

    PurchaseOutcome execute(PurchaseRequest request) throws SQLException {
        Objects.requireNonNull(request, "request must not be null");
        if (request.selectedSeats().isEmpty()) {
            throw new IllegalArgumentException("selectedSeats must not be empty");
        }

        List<Integer> seatIds = request.selectedSeats().stream().map(SelectedSeat::seatId).toList();
        try {
            Sale sale = purchaseExecutor.purchase(request.eventId(), seatIds, request.totalAmount(), request.boothId());
            try {
                seatRefresher.refreshSeats(seatIds);
            } catch (SQLException refreshEx) {
                // Sale is already committed — swallow the refresh failure so the
                // operator sees the receipt, not a misleading "purchase failed" error.
                // The seat map will self-correct on the next LISTEN/NOTIFY cycle.
                LOGGER.warn("Post-purchase seat refresh failed (sale committed): {}", refreshEx.getMessage());
            }
            return new PurchaseSuccess(PurchaseReceiptDetails.fromSale(
                    sale,
                    request.selectedSeats().stream().map(SelectedSeat::receiptLabel).toList()
            ), sale);
        } catch (SeatUnavailableException ex) {
            List<Seat> refreshedSeats = seatRefresher.refreshSeats(ex.getUnavailableSeatIds());
            SelectedSeat conflictSeat = findConflictSeat(request.selectedSeats(), ex.getUnavailableSeatIds());
            String boothLabel = "another booth";
            String timestamp = "moments ago";

            Optional<Seat> refreshedConflictSeat = refreshedSeats.stream()
                    .filter(seat -> seat.getSeatId() == conflictSeat.seatId())
                    .findFirst();
            if (refreshedConflictSeat.isPresent() && refreshedConflictSeat.get().getSaleId() != null) {
                Optional<Sale> conflictingSale = saleLookup.findSaleById(refreshedConflictSeat.get().getSaleId());
                if (conflictingSale.isPresent()) {
                    Sale sale = conflictingSale.get();
                    boothLabel = sale.getBoothId() != null && !sale.getBoothId().isBlank()
                            ? sale.getBoothId().strip()
                            : boothLabel;
                    if (sale.getSaleTimestamp() != null) {
                        timestamp = PurchaseReceiptDetails.formatTimestamp(sale.getSaleTimestamp());
                    }
                }
            }

            return new PurchaseConflict(
                    "Seat " + conflictSeat.conflictLabel()
                            + " sold by " + boothLabel
                            + " at " + timestamp
                            + ". Please select alternative seats.",
                    conflictSeat.zoneId(),
                    conflictSeat.zoneName()
            );
        }
    }

    private static SelectedSeat findConflictSeat(List<SelectedSeat> selectedSeats, List<Integer> unavailableSeatIds) {
        List<Integer> conflictIds = unavailableSeatIds != null ? unavailableSeatIds : List.of();
        for (Integer seatId : conflictIds) {
            if (seatId == null) {
                continue;
            }
            for (SelectedSeat selectedSeat : selectedSeats) {
                if (selectedSeat.seatId() == seatId) {
                    return selectedSeat;
                }
            }
        }
        return selectedSeats.getFirst();
    }

    record PurchaseRequest(int eventId, String boothId, BigDecimal totalAmount, List<SelectedSeat> selectedSeats) {
        PurchaseRequest {
            if (eventId <= 0) {
                throw new IllegalArgumentException("eventId must be positive");
            }
            boothId = boothId == null ? "" : boothId.strip();
            totalAmount = Objects.requireNonNull(totalAmount, "totalAmount must not be null");
            selectedSeats = List.copyOf(Objects.requireNonNull(selectedSeats, "selectedSeats must not be null"));
        }
    }

    record SelectedSeat(int seatId, int zoneId, String zoneName, String rowNumber, String seatNumber) {
        SelectedSeat {
            if (seatId <= 0) {
                throw new IllegalArgumentException("seatId must be positive");
            }
            if (zoneId <= 0) {
                throw new IllegalArgumentException("zoneId must be positive");
            }
            zoneName = normalizedValue(zoneName, "Unknown Zone");
            rowNumber = normalizedValue(rowNumber, "?");
            seatNumber = normalizedValue(seatNumber, "?");
        }

        String receiptLabel() {
            return zoneName + ", Row " + rowNumber + ", Seat " + seatNumber;
        }

        String conflictLabel() {
            return zoneName + "-" + rowNumber + "-" + seatNumber;
        }

        private static String normalizedValue(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.strip();
        }
    }

    sealed interface PurchaseOutcome permits PurchaseSuccess, PurchaseConflict {
    }

    record PurchaseSuccess(PurchaseReceiptDetails receiptDetails, Sale sale) implements PurchaseOutcome {
        PurchaseSuccess {
            Objects.requireNonNull(receiptDetails, "receiptDetails must not be null");
            Objects.requireNonNull(sale, "sale must not be null");
        }
    }

    record PurchaseConflict(String message, int zoneId, String zoneName) implements PurchaseOutcome {
        PurchaseConflict {
            message = Objects.requireNonNull(message, "message must not be null");
            if (zoneId <= 0) {
                throw new IllegalArgumentException("zoneId must be positive");
            }
            zoneName = zoneName == null || zoneName.isBlank() ? "this zone" : zoneName.strip();
        }

        String primaryActionLabel() {
            return "Show Available Seats in " + zoneName;
        }
    }

    @FunctionalInterface
    interface PurchaseExecutor {
        Sale purchase(int eventId, List<Integer> seatIds, BigDecimal totalAmount, String boothId)
                throws SeatUnavailableException;
    }

    @FunctionalInterface
    interface SeatRefresher {
        List<Seat> refreshSeats(List<Integer> seatIds) throws SQLException;
    }

    @FunctionalInterface
    interface SaleLookup {
        Optional<Sale> findSaleById(int saleId) throws SQLException;
    }
}
