package com.ticketsync.controller;

import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import com.ticketsync.service.SeatSyncService;
import com.ticketsync.viewmodel.SeatMapViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PosScreenCoordinatorTest {

    private SeatMapViewModel seatMapViewModel;
    private StubSeatSyncService seatSyncService;

    @BeforeEach
    void setUp() {
        seatMapViewModel = new SeatMapViewModel(
                eventId -> List.of(
                        seat(1, 10, SeatStatus.AVAILABLE),
                        seat(2, 10, SeatStatus.SOLD),
                        seat(3, 20, SeatStatus.AVAILABLE)
                ),
                eventId -> List.of(
                        zone(10, "Floor", "49.99"),
                        zone(20, "Balcony", "29.50")
                ),
                Runnable::run
        );
        seatSyncService = new StubSeatSyncService();
    }

    @Test
    void loadSelectedEvent_populatesSeatMap() throws Exception {
        PosScreenCoordinator coordinator = new PosScreenCoordinator(
                seatMapViewModel,
                seatSyncService,
                seatId -> Optional.empty()
        );

        coordinator.loadSelectedEvent(event(99, "Tonight"));

        assertEquals(3, seatMapViewModel.seatsProperty().size());
    }

    @Test
    void refreshSeat_updatesSeatSnapshot() throws Exception {
        PosScreenCoordinator coordinator = new PosScreenCoordinator(
                seatMapViewModel,
                seatSyncService,
                seatId -> Optional.of(seat(1, 10, SeatStatus.SOLD))
        );
        coordinator.loadSelectedEvent(event(99, "Tonight"));

        coordinator.refreshSeat(1);

        assertEquals(SeatStatus.SOLD, seatMapViewModel.seatsProperty().getFirst().getStatus());
    }

    @Test
    void seatSyncLifecycle_restartsAndStopsListener() {
        PosScreenCoordinator coordinator = new PosScreenCoordinator(
                seatMapViewModel,
                seatSyncService,
                seatId -> Optional.empty()
        );
        Consumer<Integer> callback = seatId -> { };

        coordinator.restartSeatSync(callback);
        coordinator.stopSeatSync();

        assertEquals(1, seatSyncService.startCalls);
        assertEquals(2, seatSyncService.stopCalls);
        assertSame(callback, seatSyncService.lastCallback);
    }

    private static Event event(int eventId, String name) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setName(name);
        return event;
    }

    private static Seat seat(int seatId, int zoneId, SeatStatus status) {
        return new Seat(seatId, zoneId, "A", String.valueOf(seatId), status, null);
    }

    private static Zone zone(int zoneId, String name, String price) {
        return new Zone(zoneId, 99, name, new BigDecimal(price));
    }

    private static final class StubSeatSyncService extends SeatSyncService {
        private int startCalls;
        private int stopCalls;
        private Consumer<Integer> lastCallback;

        @Override
        public void startListening(Consumer<Integer> callback) {
            startCalls++;
            lastCallback = callback;
        }

        @Override
        public void stopListening() {
            stopCalls++;
        }
    }
}
