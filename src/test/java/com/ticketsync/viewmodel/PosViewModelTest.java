package com.ticketsync.viewmodel;

import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.util.DatabaseHealthMonitor;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PosViewModel}.
 *
 * <p>Verifies observable list management, real-time filtering, and property
 * defaults. Does not require JavaFX toolkit initialisation because
 * {@code PosViewModel} has no direct dependencies on JavaFX UI controls.
 */
class PosViewModelTest {

    private PosViewModel viewModel;
    private SimpleBooleanProperty databaseConnected;
    private SimpleObjectProperty<DatabaseHealthMonitor.RuntimeStatus> runtimeStatus;
    private SimpleIntegerProperty retryAttemptCount;
    private SimpleLongProperty retryIntervalSeconds;
    private AtomicReference<LocalDateTime> now;

    @BeforeEach
    void setUp() {
        databaseConnected = new SimpleBooleanProperty(true);
        runtimeStatus = new SimpleObjectProperty<>(DatabaseHealthMonitor.RuntimeStatus.HEALTHY);
        retryAttemptCount = new SimpleIntegerProperty(0);
        retryIntervalSeconds = new SimpleLongProperty(30);
        now = new AtomicReference<>(LocalDateTime.of(2026, 4, 11, 14, 15, 9));
        viewModel = new PosViewModel(
                databaseConnected,
                runtimeStatus,
                retryAttemptCount,
                retryIntervalSeconds,
                now::get
        );
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Event event(String name) {
        Event e = new Event();
        e.setName(name);
        e.setEventDate(LocalDateTime.of(2026, 6, 1, 18, 0));
        return e;
    }

    private Seat seat(int seatId, SeatStatus status) {
        return new Seat(seatId, 10, "A", String.valueOf(seatId), status, null);
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    void initialState_filteredEventsIsEmpty() {
        assertTrue(viewModel.getFilteredEvents().isEmpty(),
                "filteredEvents should be empty before setEvents is called");
    }

    @Test
    void initialState_selectedEventIsNull() {
        assertNull(viewModel.selectedEventProperty().get(),
                "selectedEvent should be null initially");
    }

    @Test
    void initialState_contextPropertiesExposeHealthyDefaults() {
        assertEquals("Event: No event selected", viewModel.selectedEventTextProperty().get());
        assertEquals("Available Seats: 0", viewModel.availableSeatCountTextProperty().get());
        assertEquals("Booth: Unassigned", viewModel.boothIdTextProperty().get());
        assertEquals("DB: Connected - ACID Protected", viewModel.databaseStatusTextProperty().get());
        assertEquals("DB Connected", viewModel.systemHealthBadgeTextProperty().get());
        assertEquals(PosViewModel.SystemHealthState.HEALTHY, viewModel.systemHealthStateProperty().get());
        assertEquals("", viewModel.systemHealthBannerTextProperty().get());
        assertFalse(viewModel.systemHealthBannerVisibleProperty().get());
        assertEquals("", viewModel.purchaseBlockedReasonTextProperty().get());
        assertEquals("Last Sync: Pending", viewModel.lastSyncTimestampTextProperty().get());
        assertTrue(viewModel.purchaseEnabledProperty().get());
        assertTrue(viewModel.databaseHealthyProperty().get());
    }

    // -------------------------------------------------------------------------
    // setEvents
    // -------------------------------------------------------------------------

    @Test
    void setEvents_populatesFilteredList() {
        viewModel.setEvents(List.of(event("Concert A"), event("Festival B")));
        assertEquals(2, viewModel.getFilteredEvents().size());
    }

    @Test
    void setEvents_emptyList_clearsFilteredList() {
        viewModel.setEvents(List.of(event("Concert A")));
        viewModel.setEvents(List.of());
        assertTrue(viewModel.getFilteredEvents().isEmpty());
    }

    @Test
    void setEvents_resetsFilterPredicate() {
        viewModel.setEvents(List.of(event("Concert A"), event("Festival B")));
        viewModel.filterEvents("concert");
        assertEquals(1, viewModel.getFilteredEvents().size(),
                "filter should narrow result before reset");

        // Calling setEvents again should reset the predicate
        viewModel.setEvents(List.of(event("Rock Festival"), event("Jazz Gala")));
        assertEquals(2, viewModel.getFilteredEvents().size(),
                "all events should be visible after setEvents resets the predicate");
    }

    @Test
    void contextProperties_updateFromSelectionSeatSnapshotAndFailSafeHealth() {
        viewModel.setBoothId("Booth 7");
        viewModel.selectedEventProperty().set(event("Spring Gala"));
        viewModel.updateAvailableSeatCount(List.of(
                seat(1, SeatStatus.AVAILABLE),
                seat(2, SeatStatus.SOLD),
                seat(3, SeatStatus.AVAILABLE)
        ));
        viewModel.markLastSyncNow();

        assertEquals("Event: Spring Gala", viewModel.selectedEventTextProperty().get());
        assertEquals(2, viewModel.availableSeatCountProperty().get());
        assertEquals("Available Seats: 2", viewModel.availableSeatCountTextProperty().get());
        assertEquals("Booth: Booth 7", viewModel.boothIdTextProperty().get());
        assertEquals("Last Sync: 2026-04-11 14:15:09", viewModel.lastSyncTimestampTextProperty().get());

        databaseConnected.set(false);
        runtimeStatus.set(DatabaseHealthMonitor.RuntimeStatus.FAIL_SAFE);
        retryAttemptCount.set(1);
        retryIntervalSeconds.set(10);

        assertFalse(viewModel.purchaseEnabledProperty().get());
        assertFalse(viewModel.databaseHealthyProperty().get());
        assertEquals(PosViewModel.SystemHealthState.FAIL_SAFE, viewModel.systemHealthStateProperty().get());
        assertEquals("DB: Offline - Sales Paused", viewModel.databaseStatusTextProperty().get());
        assertEquals("Fail-Safe Active", viewModel.systemHealthBadgeTextProperty().get());
        assertTrue(viewModel.systemHealthBannerVisibleProperty().get());
        assertEquals(
            "Database offline. Sales paused while TicketSync enters fail-safe mode.",
            viewModel.systemHealthBannerTextProperty().get()
        );
        assertEquals(
            "Sales are paused while TicketSync reconnects to the database. Retry checks run every 10 seconds.",
            viewModel.purchaseBlockedReasonTextProperty().get()
        );
        }

        @Test
        void reconnectingAndRecoveryStates_updateOperatorMessaging() {
        databaseConnected.set(false);
        runtimeStatus.set(DatabaseHealthMonitor.RuntimeStatus.FAIL_SAFE);
        retryAttemptCount.set(1);
        retryIntervalSeconds.set(10);

        runtimeStatus.set(DatabaseHealthMonitor.RuntimeStatus.RECONNECTING);
        retryAttemptCount.set(2);

        assertEquals(PosViewModel.SystemHealthState.RECONNECTING, viewModel.systemHealthStateProperty().get());
        assertEquals("Reconnecting...", viewModel.systemHealthBadgeTextProperty().get());
        assertEquals("DB: Reconnecting - Sales Paused", viewModel.databaseStatusTextProperty().get());
        assertEquals(
            "Reconnecting to the database (attempt 2). Sales remain paused.",
            viewModel.systemHealthBannerTextProperty().get()
        );
        assertEquals(
            "Sales are paused while TicketSync reconnects to the database. Retry attempt 2 is in progress.",
            viewModel.purchaseBlockedReasonTextProperty().get()
        );

        databaseConnected.set(true);
        runtimeStatus.set(DatabaseHealthMonitor.RuntimeStatus.HEALTHY);
        retryAttemptCount.set(0);
        retryIntervalSeconds.set(30);

        assertEquals(PosViewModel.SystemHealthState.RESTORED, viewModel.systemHealthStateProperty().get());
        assertEquals("System Online", viewModel.systemHealthBadgeTextProperty().get());
        assertEquals("System Online - Sales resumed", viewModel.systemHealthBannerTextProperty().get());
        assertTrue(viewModel.systemHealthBannerVisibleProperty().get());

        viewModel.acknowledgeRestoredState();

        assertEquals(PosViewModel.SystemHealthState.HEALTHY, viewModel.systemHealthStateProperty().get());
        assertEquals("DB Connected", viewModel.systemHealthBadgeTextProperty().get());
        assertEquals("", viewModel.systemHealthBannerTextProperty().get());
        assertFalse(viewModel.systemHealthBannerVisibleProperty().get());
        assertEquals("", viewModel.purchaseBlockedReasonTextProperty().get());
    }

    // -------------------------------------------------------------------------
    // filterEvents
    // -------------------------------------------------------------------------

    @Test
    void filterEvents_matchingQuery_filtersCorrectly() {
        viewModel.setEvents(List.of(event("Spring Concert"), event("Summer Festival"), event("Spring Gala")));
        viewModel.filterEvents("spring");
        assertEquals(2, viewModel.getFilteredEvents().size());
        assertTrue(viewModel.getFilteredEvents().stream()
                .allMatch(e -> e.getName().toLowerCase().contains("spring")));
    }

    @Test
    void filterEvents_caseInsensitive() {
        viewModel.setEvents(List.of(event("Spring Concert"), event("Summer Festival")));
        viewModel.filterEvents("SPRING");
        assertEquals(1, viewModel.getFilteredEvents().size());
        assertEquals("Spring Concert", viewModel.getFilteredEvents().get(0).getName());
    }

    @Test
    void filterEvents_blankQuery_restoresAllEvents() {
        viewModel.setEvents(List.of(event("Concert A"), event("Festival B"), event("Concert C")));
        viewModel.filterEvents("concert");
        assertEquals(2, viewModel.getFilteredEvents().size());

        viewModel.filterEvents("");
        assertEquals(3, viewModel.getFilteredEvents().size(),
                "blank query should restore full list");
    }

    @Test
    void filterEvents_nullQuery_restoresAllEvents() {
        viewModel.setEvents(List.of(event("Concert A"), event("Festival B")));
        viewModel.filterEvents("concert");
        assertEquals(1, viewModel.getFilteredEvents().size());

        viewModel.filterEvents(null);
        assertEquals(2, viewModel.getFilteredEvents().size(),
                "null query should restore full list");
    }

    @Test
    void filterEvents_noMatch_returnsEmptyList() {
        viewModel.setEvents(List.of(event("Concert A"), event("Festival B")));
        viewModel.filterEvents("xyz");
        assertTrue(viewModel.getFilteredEvents().isEmpty(),
                "non-matching query should yield an empty filtered list");
    }

    @Test
    void filterEvents_partialMatch_returnsSubset() {
        viewModel.setEvents(List.of(
                event("Spring Concert"),
                event("Spring Jazz"),
                event("Autumn Festival")
        ));
        viewModel.filterEvents("spring");
        assertEquals(2, viewModel.getFilteredEvents().size());
    }

}
