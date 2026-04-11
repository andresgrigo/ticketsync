package com.ticketsync.controller;

import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.service.SeatSyncService;
import com.ticketsync.viewmodel.SeatMapViewModel;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

final class PosScreenCoordinator {

    private final SeatMapViewModel seatMapViewModel;
    private final SeatSyncService seatSyncService;
    private final SeatLookup seatLookup;

    PosScreenCoordinator(
            SeatMapViewModel seatMapViewModel,
            SeatSyncService seatSyncService,
            SeatLookup seatLookup
    ) {
        this.seatMapViewModel = Objects.requireNonNull(seatMapViewModel, "seatMapViewModel must not be null");
        this.seatSyncService = Objects.requireNonNull(seatSyncService, "seatSyncService must not be null");
        this.seatLookup = Objects.requireNonNull(seatLookup, "seatLookup must not be null");
    }

    void loadSelectedEvent(Event event) throws SQLException {
        Objects.requireNonNull(event, "event must not be null");
        seatMapViewModel.loadEventData(event.getEventId());
    }

    void refreshSeat(int seatId) throws SQLException {
        Optional<Seat> refreshedSeat = seatLookup.findById(seatId);
        if (refreshedSeat.isEmpty()) {
            return;
        }

        seatMapViewModel.replaceSeat(refreshedSeat.get());
    }

    void restartSeatSync(Consumer<Integer> seatUpdateCallback) {
        Objects.requireNonNull(seatUpdateCallback, "seatUpdateCallback must not be null");
        seatSyncService.stopListening();
        seatSyncService.startListening(seatUpdateCallback);
    }

    void stopSeatSync() {
        seatSyncService.stopListening();
    }

    @FunctionalInterface
    interface SeatLookup {
        Optional<Seat> findById(int seatId) throws SQLException;
    }
}
