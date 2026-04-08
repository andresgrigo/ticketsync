package com.ticketsync.viewmodel;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SeatManagementViewModel}.
 *
 * <p>{@code FXCollections.observableArrayList()} and the Simple*Property
 * classes work without initialising the JavaFX toolkit, so no
 * {@code Platform.startup()} guard is needed here.
 */
class SeatManagementViewModelTest {

    private SeatManagementViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new SeatManagementViewModel();
    }

    @Test
    void initialState_seatsListIsEmpty() {
        assertTrue(viewModel.seatsProperty().isEmpty(), "seats list should start empty");
    }

    @Test
    void initialState_loadingIsFalse() {
        assertFalse(viewModel.loadingProperty().get(), "loading should start false");
    }

    @Test
    void setSeats_populatesList() {
        Seat s1 = seat(1, 5, "A", "1");
        Seat s2 = seat(2, 5, "A", "2");

        viewModel.setSeats(List.of(s1, s2));

        assertEquals(2, viewModel.seatsProperty().size());
    }

    @Test
    void setSeats_replacesExistingList() {
        viewModel.setSeats(List.of(seat(1, 5, "A", "1")));
        viewModel.setSeats(List.of(seat(2, 5, "B", "1"), seat(3, 5, "B", "2")));

        assertEquals(2, viewModel.seatsProperty().size());
    }

    @Test
    void setSeats_nullList_resultsInEmptyList() {
        viewModel.setSeats(List.of(seat(1, 5, "A", "1")));
        viewModel.setSeats(null);

        assertTrue(viewModel.seatsProperty().isEmpty());
    }

    @Test
    void setSeats_emptyList_clearsExistingList() {
        viewModel.setSeats(List.of(seat(1, 5, "A", "1")));
        viewModel.setSeats(List.of());

        assertTrue(viewModel.seatsProperty().isEmpty());
    }

    @Test
    void setSeats_preservesOrder() {
        Seat s1 = seat(1, 5, "A", "1");
        Seat s2 = seat(2, 5, "A", "2");
        Seat s3 = seat(3, 5, "B", "1");

        viewModel.setSeats(List.of(s1, s2, s3));

        assertEquals(s1, viewModel.seatsProperty().get(0));
        assertEquals(s2, viewModel.seatsProperty().get(1));
        assertEquals(s3, viewModel.seatsProperty().get(2));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Seat seat(int seatId, int zoneId, String row, String number) {
        return new Seat(seatId, zoneId, row, number, SeatStatus.AVAILABLE, null);
    }
}
