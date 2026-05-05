package com.ticketsync.viewmodel;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Derives selection-panel state from {@link SeatMapViewModel}'s local seat selection.
 *
 * <p>The countdown and release behavior remain local to the workstation. This
 * class does not persist RESERVED seats or submit purchases.
 */
public class SelectionPanelViewModel implements AutoCloseable {

    private static final int LOCK_DURATION_SECONDS = 60;
    private static final BigDecimal ZERO_PRICE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final SeatMapViewModel seatMapViewModel;
    private final Consumer<Runnable> uiRunner;
    private final CountdownScheduler countdownScheduler;
    private final BooleanProperty purchaseEnabled = new SimpleBooleanProperty(false);

    private final ObservableList<String> selectedSeatRows = FXCollections.observableArrayList();
    private final ObservableList<String> readOnlySelectedSeatRows =
            FXCollections.unmodifiableObservableList(selectedSeatRows);

    private final ReadOnlyIntegerWrapper seatCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyObjectWrapper<BigDecimal> totalPrice = new ReadOnlyObjectWrapper<>(ZERO_PRICE);
    private final ReadOnlyIntegerWrapper remainingLockSeconds = new ReadOnlyIntegerWrapper(LOCK_DURATION_SECONDS);
    private final ReadOnlyBooleanWrapper lockActive = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper processing = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper headerText = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper totalPriceText = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper countdownText = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper warningState = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper emptyStateVisible = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyBooleanWrapper confirmEnabled = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper releaseEnabled = new ReadOnlyBooleanWrapper(false);

    private final SetChangeListener<Integer> selectedSeatListener = change -> refreshFromSeatMap(true);
    private final ListChangeListener<Seat> seatListListener = change -> refreshFromSeatMap(false);
    private final ListChangeListener<Zone> zoneListListener = change -> refreshFromSeatMap(false);

    private CountdownHandle countdownHandle;
    private volatile int currentGeneration = 0;
    private volatile boolean disposed = false;
    private Runnable onConfirmAction;
    private Runnable onReleaseAction;

    /**
     * Creates a production-ready view-model wired to the given {@link SeatMapViewModel}.
     *
     * <p>Uses {@link Platform#runLater(Runnable)} for UI-thread dispatch and an
     * internal scheduled executor for the lock-countdown timer.
     *
     * @param seatMapViewModel the source of seat selection state; must not be {@code null}
     * @throws NullPointerException if {@code seatMapViewModel} is {@code null}
     */
    public SelectionPanelViewModel(SeatMapViewModel seatMapViewModel) {
        this(
                seatMapViewModel,
                null,
                runnable -> {
                    if (Platform.isFxApplicationThread()) {
                        runnable.run();
                    } else {
                        Platform.runLater(runnable);
                    }
                },
                new ExecutorCountdownScheduler()
        );
    }

    SelectionPanelViewModel(
            SeatMapViewModel seatMapViewModel,
            ObservableBooleanValue purchaseEnabledGate,
            Consumer<Runnable> uiRunner,
            CountdownScheduler countdownScheduler
    ) {
        this.seatMapViewModel = Objects.requireNonNull(seatMapViewModel, "seatMapViewModel must not be null");
        this.uiRunner = Objects.requireNonNull(uiRunner, "uiRunner must not be null");
        this.countdownScheduler = Objects.requireNonNull(countdownScheduler, "countdownScheduler must not be null");

        bindDerivedProperties();

        seatMapViewModel.selectedSeatIdsProperty().addListener(selectedSeatListener);
        seatMapViewModel.seatsProperty().addListener(seatListListener);
        seatMapViewModel.zonesProperty().addListener(zoneListListener);

        if (purchaseEnabledGate != null) {
            bindPurchaseEnabled(purchaseEnabledGate);
        }

        refreshFromSeatMap(false);
    }

    /**
     * Returns the read-only list of formatted display rows for the currently selected seats.
     *
     * @return unmodifiable observable list; never {@code null}
     */
    public ObservableList<String> selectedSeatRowsProperty() {
        return readOnlySelectedSeatRows;
    }

    /**
     * Returns the read-only property holding the number of currently selected seats.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyIntegerProperty seatCountProperty() {
        return seatCount.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property holding the sum price of all selected seats.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyObjectProperty<BigDecimal> totalPriceProperty() {
        return totalPrice.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property holding the number of seconds remaining before
     * the local seat-lock expires.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyIntegerProperty remainingLockSecondsProperty() {
        return remainingLockSeconds.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property that is {@code true} when the lock is active
     * and fewer than 10 seconds remain, triggering a visual warning.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyBooleanProperty warningStateProperty() {
        return warningState.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property that is {@code true} while the local seat-lock countdown is running.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyBooleanProperty lockActiveProperty() {
        return lockActive.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property that is {@code true} when no seats are selected,
     * indicating the panel should show its empty-state placeholder.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyBooleanProperty emptyStateVisibleProperty() {
        return emptyStateVisible.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property that is {@code true} while a purchase submission is in progress.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyBooleanProperty processingProperty() {
        return processing.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property that is {@code true} when the confirm-purchase action is available.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyBooleanProperty confirmEnabledProperty() {
        return confirmEnabled.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property that is {@code true} when the release-lock action is available.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyBooleanProperty releaseEnabledProperty() {
        return releaseEnabled.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property containing the selection-panel header text,
     * e.g. {@code "Selected Seats (3 seats)"}.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyStringProperty headerTextProperty() {
        return headerText.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property containing the formatted total price string,
     * e.g. {@code "Total: EUR25.00"}.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyStringProperty totalPriceTextProperty() {
        return totalPriceText.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property containing the formatted countdown text,
     * e.g. {@code "Lock expires in: 42"}.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyStringProperty countdownTextProperty() {
        return countdownText.getReadOnlyProperty();
    }

    /**
     * Binds the confirm-purchase availability to an external observable gate,
     * e.g. a database-health monitor's connected property.
     *
     * @param purchaseEnabledGate observable controlling purchase availability; must not be {@code null}
     * @throws NullPointerException if {@code purchaseEnabledGate} is {@code null}
     */
    public void bindPurchaseEnabled(ObservableBooleanValue purchaseEnabledGate) {
        Objects.requireNonNull(purchaseEnabledGate, "purchaseEnabledGate must not be null");
        purchaseEnabled.unbind();
        purchaseEnabled.bind(purchaseEnabledGate);
    }

    /**
     * Sets whether a purchase submission is currently in progress.
     *
     * @param processing {@code true} to show the processing overlay; {@code false} to hide it
     */
    public void setProcessing(boolean processing) {
        uiRunner.accept(() -> this.processing.set(processing));
    }

    /**
     * Registers the action to invoke when the user confirms a purchase.
     *
     * @param onConfirmAction runnable invoked on the UI thread; may be {@code null} to clear
     */
    public void setOnConfirmAction(Runnable onConfirmAction) {
        this.onConfirmAction = onConfirmAction;
    }

    /**
     * Registers the action to invoke when the user releases the seat lock.
     *
     * @param onReleaseAction runnable invoked on the UI thread; may be {@code null} to clear
     */
    public void setOnReleaseAction(Runnable onReleaseAction) {
        this.onReleaseAction = onReleaseAction;
    }

    /**
     * Triggers the confirm-purchase action if the confirm button is currently enabled.
     *
     * <p>No-ops silently if confirm is disabled or no confirm action has been registered.
     */
    public void confirmSelection() {
        if (!confirmEnabled.get()) {
            return;
        }
        if (onConfirmAction != null) {
            onConfirmAction.run();
        }
    }

    /**
     * Triggers the release action if the release button is currently enabled.
     *
     * <p>Clears the seat selection on the underlying {@link SeatMapViewModel} and invokes
     * the registered release action (if any). No-ops silently when release is disabled.
     */
    public void releaseSelection() {
        if (!releaseEnabled.get()) {
            return;
        }
        seatMapViewModel.clearSelection();
        if (onReleaseAction != null) {
            onReleaseAction.run();
        }
    }

    /**
     * Clears the processing flag and seat selection, returning the panel to the ready state.
     *
     * <p>Typically called after a failed or cancelled purchase attempt.
     */
    public void resetToReadyState() {
        setProcessing(false);
        seatMapViewModel.clearSelection();
    }

    /**
     * Detaches listeners from the source view-model and stops the countdown timer.
     *
     * <p>Must be called when this view-model is no longer needed to prevent memory leaks.
     * Implements {@link AutoCloseable} so this view-model can be used in try-with-resources.
     */
    public void dispose() {
        disposed = true;
        seatMapViewModel.selectedSeatIdsProperty().removeListener(selectedSeatListener);
        seatMapViewModel.seatsProperty().removeListener(seatListListener);
        seatMapViewModel.zonesProperty().removeListener(zoneListListener);
        stopAndResetCountdown();
        countdownScheduler.shutdown();
    }

    @Override
    public void close() {
        dispose();
    }

    private void bindDerivedProperties() {
        headerText.bind(Bindings.createStringBinding(
                () -> "Selected Seats (" + seatCount.get() + " seats)",
                seatCount
        ));
        totalPriceText.bind(Bindings.createStringBinding(
                () -> "Total: " + formatCurrency(totalPrice.get()),
                totalPrice
        ));
        countdownText.bind(Bindings.createStringBinding(
                () -> "Lock expires in: " + remainingLockSeconds.get(),
                remainingLockSeconds
        ));
        warningState.bind(Bindings.createBooleanBinding(
                () -> lockActive.get() && remainingLockSeconds.get() < 10,
                lockActive,
                remainingLockSeconds
        ));
        emptyStateVisible.bind(seatCount.isEqualTo(0));
        confirmEnabled.bind(Bindings.createBooleanBinding(
                () -> seatCount.get() > 0
                        && lockActive.get()
                        && !processing.get()
                        && purchaseEnabled.get(),
                seatCount,
                lockActive,
                processing,
                purchaseEnabled
        ));
        releaseEnabled.bind(Bindings.createBooleanBinding(
                () -> seatCount.get() > 0 && !processing.get(),
                seatCount,
                processing
        ));
    }

    private void refreshFromSeatMap(boolean selectionChanged) {
        uiRunner.accept(() -> {
            List<SelectedSeatSummary> summaries = buildSelectedSeatSummaries();
            selectedSeatRows.setAll(summaries.stream().map(SelectedSeatSummary::displayRow).toList());
            seatCount.set(summaries.size());
            totalPrice.set(summaries.stream()
                    .map(SelectedSeatSummary::price)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP));

            if (summaries.isEmpty()) {
                stopAndResetCountdown();
            } else if (selectionChanged) {
                restartCountdown();
            }
        });
    }

    private List<SelectedSeatSummary> buildSelectedSeatSummaries() {
        List<SelectedSeatSummary> summaries = new ArrayList<>();
        for (Seat seat : seatMapViewModel.seatsProperty()) {
            if (!seatMapViewModel.selectedSeatIdsProperty().contains(seat.getSeatId())) {
                continue;
            }
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                continue;
            }

            Zone zone = seatMapViewModel.getZone(seat.getZoneId());
            String zoneName = zone != null && zone.getName() != null && !zone.getName().isBlank()
                    ? zone.getName()
                    : "Unknown Zone";
            BigDecimal zonePrice = zone != null && zone.getPrice() != null ? zone.getPrice() : ZERO_PRICE;
            String rowLabel = seat.getRowNumber() != null ? seat.getRowNumber() : "";
            String seatLabel = seat.getSeatNumber() != null ? seat.getSeatNumber() : "";
            summaries.add(new SelectedSeatSummary(
                    zonePrice,
                    zoneName + " Row " + rowLabel + " Seat " + seatLabel + " - " + formatCurrency(zonePrice)
            ));
        }
        return summaries;
    }

    private void restartCountdown() {
        cancelCountdown();
        remainingLockSeconds.set(LOCK_DURATION_SECONDS);
        lockActive.set(true);
        final int gen = ++currentGeneration;
        countdownHandle = countdownScheduler.scheduleAtFixedRate(
                () -> uiRunner.accept(() -> handleCountdownTick(gen)),
                1,
                1
        );
    }

    private void handleCountdownTick(int generation) {
        if (disposed || generation != currentGeneration) {
            return;
        }
        if (seatCount.get() == 0) {
            stopAndResetCountdown();
            return;
        }

        int nextValue = remainingLockSeconds.get() - 1;
        if (nextValue <= 0) {
            stopAndResetCountdown();
            seatMapViewModel.clearSelection();
            return;
        }
        remainingLockSeconds.set(nextValue);
    }

    private void stopAndResetCountdown() {
        cancelCountdown();
        remainingLockSeconds.set(LOCK_DURATION_SECONDS);
        lockActive.set(false);
    }

    private void cancelCountdown() {
        if (countdownHandle != null) {
            countdownHandle.cancel();
            countdownHandle = null;
        }
    }

    private static String formatCurrency(BigDecimal amount) {
        BigDecimal normalized = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : ZERO_PRICE;
        return "EUR" + normalized.toPlainString();
    }

    private record SelectedSeatSummary(BigDecimal price, String displayRow) {
    }

    interface CountdownScheduler {
        CountdownHandle scheduleAtFixedRate(Runnable task, long initialDelaySeconds, long periodSeconds);

        void shutdown();
    }

    @FunctionalInterface
    interface CountdownHandle {
        void cancel();
    }

    private static final class ExecutorCountdownScheduler implements CountdownScheduler {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SelectionPanel-LockTimer");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public CountdownHandle scheduleAtFixedRate(Runnable task, long initialDelaySeconds, long periodSeconds) {
            ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                    task,
                    initialDelaySeconds,
                    periodSeconds,
                    TimeUnit.SECONDS
            );
            return () -> future.cancel(false);
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}
