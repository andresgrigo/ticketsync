package com.ticketsync.viewmodel;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SeatMapViewModelTest {

    private SeatMapViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new SeatMapViewModel(
                eventId -> List.of(
                        seat(1, 10, "A", "1", SeatStatus.AVAILABLE),
                        seat(2, 10, "A", "2", SeatStatus.SOLD),
                        seat(3, 20, "B", "1", SeatStatus.DISABLED)
                ),
                eventId -> List.of(
                        zone(10, "Floor", "49.99"),
                        zone(20, "Balcony", "29.50")
                ),
                Runnable::run
        );
    }

    @Test
    void loadEventData_populatesReadOnlySeatAndZoneState() throws Exception {
        viewModel.loadEventData(99);

        assertEquals(3, viewModel.seatsProperty().size());
        assertEquals(2, viewModel.zonesProperty().size());
        assertEquals("Floor", viewModel.getZone(10).getName());
        assertFalse(viewModel.loadingProperty().get());
        assertEquals(1, viewModel.focusedSeatIdProperty().get());
        assertEquals(0, viewModel.focusedSeatIndexProperty().get());

        assertThrows(UnsupportedOperationException.class,
                () -> viewModel.seatsProperty().add(seat(99, 10, "Z", "9", SeatStatus.AVAILABLE)));
        assertThrows(UnsupportedOperationException.class,
                () -> viewModel.selectedSeatIdsProperty().add(1));
    }

    @Test
    void toggleSeatSelection_availableSeatTogglesWithoutChangingPersistedStatus() throws Exception {
        viewModel.loadEventData(99);

        assertTrue(viewModel.toggleSeatSelection(1));
        assertEquals(Set.of(1), viewModel.selectedSeatIdsProperty());
        assertEquals(SeatStatus.AVAILABLE, viewModel.seatsProperty().getFirst().getStatus());

        assertFalse(viewModel.toggleSeatSelection(1));
        assertTrue(viewModel.selectedSeatIdsProperty().isEmpty());
        assertEquals(SeatStatus.AVAILABLE, viewModel.seatsProperty().getFirst().getStatus());
    }

    @Test
    void toggleSeatSelection_ignoresSoldAndDisabledSeats() throws Exception {
        viewModel.loadEventData(99);

        assertFalse(viewModel.toggleSeatSelection(2));
        assertFalse(viewModel.toggleSeatSelection(3));
        assertTrue(viewModel.selectedSeatIdsProperty().isEmpty());
    }

    @Test
    void updateSeatStatus_clearsLocalSelectionWhenSeatStopsBeingAvailable() throws Exception {
        viewModel.loadEventData(99);
        viewModel.toggleSeatSelection(1);

        viewModel.updateSeatStatus(1, SeatStatus.SOLD);

        assertEquals(SeatStatus.SOLD, viewModel.seatsProperty().getFirst().getStatus());
        assertTrue(viewModel.selectedSeatIdsProperty().isEmpty());
    }

    @Test
    void replaceSeat_updatesSeatSnapshotAndPreservesFocusBySeatId() throws Exception {
        viewModel.loadEventData(99);
        viewModel.setFocusedSeatId(3);

        viewModel.replaceSeat(seat(3, 20, "B", "1", SeatStatus.AVAILABLE));

        assertEquals(3, viewModel.focusedSeatIdProperty().get());
        assertEquals(2, viewModel.focusedSeatIndexProperty().get());
        assertEquals(SeatStatus.AVAILABLE, viewModel.seatsProperty().get(2).getStatus());
    }

    @Test
    void showAvailableSeatsInZone_filtersRenderedSeatsWithoutChangingCanonicalSnapshot() throws Exception {
        viewModel.loadEventData(99);

        viewModel.showAvailableSeatsInZone(10);

        assertEquals(List.of(1), viewModel.renderedSeatsProperty().stream().map(Seat::getSeatId).toList());
        assertEquals(List.of(1, 2, 3), viewModel.seatsProperty().stream().map(Seat::getSeatId).toList());

        viewModel.clearRecoveryFilter();

        assertEquals(List.of(1, 2, 3), viewModel.renderedSeatsProperty().stream().map(Seat::getSeatId).toList());
    }

    @Test
    void toggleSeatSelection_clearsRecoveryFilterWhenNewSelectionBegins() throws Exception {
        viewModel.loadEventData(99);
        viewModel.showAvailableSeatsInZone(10);

        assertEquals(List.of(1), viewModel.renderedSeatsProperty().stream().map(Seat::getSeatId).toList());

        assertTrue(viewModel.toggleSeatSelection(1));

        assertEquals(Set.of(1), viewModel.selectedSeatIdsProperty());
        assertEquals(List.of(1, 2, 3), viewModel.renderedSeatsProperty().stream().map(Seat::getSeatId).toList());
    }

    @Test
    void loadEventData_failureResetsLoadingState() {
        SeatMapViewModel failing = new SeatMapViewModel(
                eventId -> {
                    throw new SQLException("boom");
                },
                eventId -> List.of(),
                Runnable::run
        );

        SQLException error = assertThrows(SQLException.class, () -> failing.loadEventData(42));
        assertEquals("boom", error.getMessage());
        assertFalse(failing.loadingProperty().get());
    }

    private static Seat seat(int seatId, int zoneId, String row, String number, SeatStatus status) {
        return new Seat(seatId, zoneId, row, number, status, null);
    }

    private static Zone zone(int zoneId, String name, String price) {
        return new Zone(zoneId, 1, name, new BigDecimal(price));
    }
}
