package com.ticketsync.viewmodel;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SelectionPanelViewModelTest {

    private SeatMapViewModel seatMapViewModel;
    private SimpleBooleanProperty purchaseEnabled;
    private FakeCountdownScheduler countdownScheduler;
    private SelectionPanelViewModel viewModel;

    @BeforeEach
    void setUp() throws Exception {
        seatMapViewModel = new SeatMapViewModel(
                eventId -> List.of(
                        seat(1, 10, "A", "1", SeatStatus.AVAILABLE),
                        seat(2, 10, "A", "2", SeatStatus.SOLD),
                        seat(3, 20, "B", "1", SeatStatus.AVAILABLE)
                ),
                eventId -> List.of(
                        zone(10, "Floor", "49.99"),
                        zone(20, "Balcony", "29.50")
                ),
                Runnable::run
        );
        seatMapViewModel.loadEventData(99);

        purchaseEnabled = new SimpleBooleanProperty(true);
        countdownScheduler = new FakeCountdownScheduler();
        viewModel = new SelectionPanelViewModel(seatMapViewModel, purchaseEnabled, Runnable::run, countdownScheduler);
    }

    @Test
    void initialState_showsEmptyStateWithDisabledActions() {
        assertTrue(viewModel.emptyStateVisibleProperty().get());
        assertEquals(0, viewModel.seatCountProperty().get());
        assertTrue(viewModel.selectedSeatRowsProperty().isEmpty());
        assertFalse(viewModel.lockActiveProperty().get());
        assertFalse(viewModel.confirmEnabledProperty().get());
        assertFalse(viewModel.releaseEnabledProperty().get());
        assertEquals(60, viewModel.remainingLockSecondsProperty().get());
        assertEquals("Selected Seats (0 seats)", viewModel.headerTextProperty().get());
    }

    @Test
    void selectedSeats_deriveRowsAndTotalFromSeatMapState() {
        seatMapViewModel.toggleSeatSelection(3);
        seatMapViewModel.toggleSeatSelection(1);

        assertEquals(2, viewModel.seatCountProperty().get());
        assertEquals(
                List.of(
                        "Floor Row A Seat 1 - EUR49.99",
                        "Balcony Row B Seat 1 - EUR29.50"
                ),
                List.copyOf(viewModel.selectedSeatRowsProperty())
        );
        assertEquals("Selected Seats (2 seats)", viewModel.headerTextProperty().get());
        assertEquals("Total: EUR79.49", viewModel.totalPriceTextProperty().get());
        assertTrue(viewModel.lockActiveProperty().get());
        assertTrue(viewModel.confirmEnabledProperty().get());
        assertTrue(viewModel.releaseEnabledProperty().get());
        assertFalse(viewModel.emptyStateVisibleProperty().get());
    }

    @Test
    void selectionChanges_restartCountdownAtSixtySeconds() {
        seatMapViewModel.toggleSeatSelection(1);

        assertEquals(1, countdownScheduler.scheduleCount());
        assertTrue(countdownScheduler.isScheduled());
        assertEquals(60, viewModel.remainingLockSecondsProperty().get());

        countdownScheduler.tick(5);
        assertEquals(55, viewModel.remainingLockSecondsProperty().get());

        seatMapViewModel.toggleSeatSelection(3);

        assertEquals(2, countdownScheduler.scheduleCount());
        assertEquals(60, viewModel.remainingLockSecondsProperty().get());
    }

    @Test
    void countdownWarningAndExpiry_autoReleaseSelection() {
        seatMapViewModel.toggleSeatSelection(1);

        countdownScheduler.tick(51);
        assertEquals(9, viewModel.remainingLockSecondsProperty().get());
        assertTrue(viewModel.warningStateProperty().get());
        assertTrue(viewModel.lockActiveProperty().get());

        countdownScheduler.tick(9);
        assertTrue(seatMapViewModel.selectedSeatIdsProperty().isEmpty());
        assertTrue(viewModel.emptyStateVisibleProperty().get());
        assertFalse(viewModel.lockActiveProperty().get());
        assertFalse(countdownScheduler.isScheduled());
        assertEquals(60, viewModel.remainingLockSecondsProperty().get());
    }

    @Test
    void manualRelease_clearsSelectionAndStopsTimer() {
        seatMapViewModel.toggleSeatSelection(1);
        seatMapViewModel.toggleSeatSelection(3);
        countdownScheduler.tick(3);

        viewModel.releaseSelection();

        assertTrue(seatMapViewModel.selectedSeatIdsProperty().isEmpty());
        assertTrue(viewModel.emptyStateVisibleProperty().get());
        assertFalse(viewModel.lockActiveProperty().get());
        assertFalse(countdownScheduler.isScheduled());
        assertEquals(60, viewModel.remainingLockSecondsProperty().get());
    }

    @Test
    void confirmAndReleaseActions_respectGatesAndProcessingState() {
        AtomicInteger confirmCalls = new AtomicInteger();
        AtomicInteger releaseCalls = new AtomicInteger();
        viewModel.setOnConfirmAction(confirmCalls::incrementAndGet);
        viewModel.setOnReleaseAction(releaseCalls::incrementAndGet);

        seatMapViewModel.toggleSeatSelection(1);
        viewModel.confirmSelection();
        assertEquals(1, confirmCalls.get());

        purchaseEnabled.set(false);
        assertFalse(viewModel.confirmEnabledProperty().get());
        viewModel.confirmSelection();
        assertEquals(1, confirmCalls.get());

        purchaseEnabled.set(true);
        viewModel.setProcessing(true);
        assertFalse(viewModel.confirmEnabledProperty().get());
        assertFalse(viewModel.releaseEnabledProperty().get());

        viewModel.releaseSelection();
        assertEquals(0, releaseCalls.get());

        viewModel.setProcessing(false);
        viewModel.releaseSelection();
        assertEquals(1, releaseCalls.get());
    }

    @Test
    void seatBecomingUnavailable_updatesPanelStateFromSeatMapPruning() {
        seatMapViewModel.toggleSeatSelection(1);

        seatMapViewModel.updateSeatStatus(1, SeatStatus.SOLD);

        assertTrue(viewModel.selectedSeatRowsProperty().isEmpty());
        assertEquals("Total: EUR0.00", viewModel.totalPriceTextProperty().get());
        assertTrue(viewModel.emptyStateVisibleProperty().get());
        assertFalse(viewModel.lockActiveProperty().get());
        assertFalse(countdownScheduler.isScheduled());
    }

    @Test
    void resetToReadyState_clearsSelectionProcessingAndCountdown() {
        seatMapViewModel.toggleSeatSelection(1);
        seatMapViewModel.toggleSeatSelection(3);
        countdownScheduler.tick(4);
        viewModel.setProcessing(true);

        viewModel.resetToReadyState();

        assertFalse(viewModel.processingProperty().get());
        assertTrue(seatMapViewModel.selectedSeatIdsProperty().isEmpty());
        assertTrue(viewModel.emptyStateVisibleProperty().get());
        assertFalse(viewModel.lockActiveProperty().get());
        assertFalse(countdownScheduler.isScheduled());
        assertEquals(60, viewModel.remainingLockSecondsProperty().get());
    }

    @Test
    void dispose_stopsTimerAndDetachesSeatMapListeners() {
        seatMapViewModel.toggleSeatSelection(1);
        assertTrue(countdownScheduler.isScheduled());

        viewModel.dispose();

        assertFalse(countdownScheduler.isScheduled());
        assertTrue(countdownScheduler.isShutdown());

        seatMapViewModel.toggleSeatSelection(3);
        assertEquals(1, viewModel.seatCountProperty().get(),
                "disposed view-model should stop reacting to later seat-map changes");
    }

    private static Seat seat(int seatId, int zoneId, String row, String number, SeatStatus status) {
        return new Seat(seatId, zoneId, row, number, status, null);
    }

    private static Zone zone(int zoneId, String name, String price) {
        return new Zone(zoneId, 1, name, new BigDecimal(price));
    }

    private static final class FakeCountdownScheduler implements SelectionPanelViewModel.CountdownScheduler {
        private Runnable scheduledTask;
        private boolean scheduled;
        private boolean shutdown;
        private int scheduleCount;

        @Override
        public SelectionPanelViewModel.CountdownHandle scheduleAtFixedRate(
                Runnable task,
                long initialDelaySeconds,
                long periodSeconds
        ) {
            scheduledTask = task;
            scheduled = true;
            scheduleCount++;
            return () -> scheduled = false;
        }

        @Override
        public void shutdown() {
            scheduled = false;
            scheduledTask = null;
            shutdown = true;
        }

        int scheduleCount() {
            return scheduleCount;
        }

        boolean isScheduled() {
            return scheduled;
        }

        boolean isShutdown() {
            return shutdown;
        }

        void tick(int times) {
            for (int index = 0; index < times; index++) {
                if (!scheduled || scheduledTask == null) {
                    return;
                }
                scheduledTask.run();
            }
        }
    }
}
