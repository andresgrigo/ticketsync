package com.ticketsync.viewmodel;

import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.util.DatabaseHealthMonitor;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.binding.LongBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableIntegerValue;
import javafx.beans.value.ObservableLongValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Presentation-layer state for the Vendor POS event selector.
 *
 * <p>Holds the observable list of all active events and a displayed
 * {@link ObservableList} that the ComboBox binds to. Filtering is implemented by
 * repopulating the displayed list via {@link #filterEvents(String)}; this avoids
 * using {@code FilteredList.setPredicate()} which triggers a JavaFX 21 bug
 * ({@code ReadOnlyUnbackedObservableList.subList()} reads a stale size during
 * a ComboBox click-dispatch and throws {@code IndexOutOfBoundsException}).
 *
 * <p>This class has no reference to JavaFX UI controls and can therefore be
 * tested without initialising the JavaFX toolkit.
 *
 * <p>("POS Main View with Seat Map") will consume
 * {@link #selectedEventProperty()} to drive seat loading. Retrieve this
 * view-model from the controller via {@code PosController#getViewModel()}.
 */
public class PosViewModel {

    private static final DateTimeFormatter SYNC_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum SystemHealthState {
        HEALTHY,
        FAIL_SAFE,
        RECONNECTING,
        RESTORED
    }

    private final ObservableList<Event> allEvents = FXCollections.observableArrayList();
    private final ObservableList<Event> displayedEvents = FXCollections.observableArrayList();
    private final ObjectProperty<Event> selectedEvent = new SimpleObjectProperty<>(null);
    private final ReadOnlyBooleanWrapper purchaseEnabled = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyBooleanWrapper databaseHealthy = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyIntegerWrapper availableSeatCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyObjectWrapper<SystemHealthState> systemHealthState =
            new ReadOnlyObjectWrapper<>(SystemHealthState.HEALTHY);
    private final ReadOnlyStringWrapper selectedEventText = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper availableSeatCountText = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper boothIdText = new ReadOnlyStringWrapper("Booth: Unassigned");
    private final ReadOnlyStringWrapper databaseStatusText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper systemHealthBadgeText = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper systemHealthBannerText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper systemHealthBannerVisible = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper purchaseBlockedReasonText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper lastSyncTimestampText = new ReadOnlyStringWrapper("Last Sync: Pending");
    private final Supplier<LocalDateTime> timestampSupplier;

    public PosViewModel() {
        this(
                DatabaseHealthMonitor.getInstance().connectedProperty(),
                DatabaseHealthMonitor.getInstance().runtimeStatusProperty(),
                DatabaseHealthMonitor.getInstance().retryAttemptCountProperty(),
                DatabaseHealthMonitor.getInstance().currentCheckIntervalSecondsProperty(),
                LocalDateTime::now
        );
    }

    public PosViewModel(ObservableBooleanValue databaseConnected, Supplier<LocalDateTime> timestampSupplier) {
        this(
                databaseConnected,
                fallbackRuntimeStatus(databaseConnected),
                fallbackRetryAttemptCount(databaseConnected),
                fallbackRetryIntervalSeconds(databaseConnected),
                timestampSupplier
        );
    }

    public PosViewModel(
            ObservableBooleanValue databaseConnected,
            ObservableObjectValue<DatabaseHealthMonitor.RuntimeStatus> runtimeStatus,
            ObservableIntegerValue retryAttemptCount,
            ObservableLongValue retryIntervalSeconds,
            Supplier<LocalDateTime> timestampSupplier
    ) {
        Objects.requireNonNull(databaseConnected, "databaseConnected must not be null");
        Objects.requireNonNull(runtimeStatus, "runtimeStatus must not be null");
        Objects.requireNonNull(retryAttemptCount, "retryAttemptCount must not be null");
        Objects.requireNonNull(retryIntervalSeconds, "retryIntervalSeconds must not be null");
        this.timestampSupplier = Objects.requireNonNull(timestampSupplier, "timestampSupplier must not be null");

        purchaseEnabled.bind(databaseConnected);
        databaseHealthy.bind(databaseConnected);

        selectedEventText.bind(Bindings.createStringBinding(
                () -> {
                    Event selected = selectedEvent.get();
                    return selected != null && selected.getName() != null && !selected.getName().isBlank()
                            ? "Event: " + selected.getName()
                            : "Event: No event selected";
                },
                selectedEvent
        ));
        availableSeatCountText.bind(Bindings.createStringBinding(
                () -> "Available Seats: " + availableSeatCount.get(),
                availableSeatCount
        ));

        runtimeStatus.addListener((obs, oldValue, newValue) -> {
            applyRuntimeStatusTransition(oldValue, newValue);
            refreshHealthCopy(retryAttemptCount, retryIntervalSeconds);
        });
        retryAttemptCount.addListener((obs, oldValue, newValue) -> refreshHealthCopy(retryAttemptCount, retryIntervalSeconds));
        retryIntervalSeconds.addListener((obs, oldValue, newValue) -> refreshHealthCopy(retryAttemptCount, retryIntervalSeconds));

        applyRuntimeStatusTransition(null, runtimeStatus.getValue());
        refreshHealthCopy(retryAttemptCount, retryIntervalSeconds);
    }

    /**
     * Returns the displayed events list that the {@code ComboBox} binds to.
     *
     * <p>Updated by {@link #setEvents(List)} on load and by
     * {@link #filterEvents(String)} whenever the user types in the search field.
     *
     * @return the displayed events list; never {@code null}
     */
    public ObservableList<Event> getFilteredEvents() {
        return displayedEvents;
    }

    /**
     * Replaces the backing list with the supplied events and resets the filter
     * predicate so that all items are visible after a fresh load.
     *
     * @param events the new list of active events; must not be {@code null}
     */
    public void setEvents(List<Event> events) {
        allEvents.setAll(events);
        displayedEvents.setAll(events);
    }

    /**
     * Repopulates the displayed list with events whose name contains {@code query}
     * (case-insensitive). Passing a blank or {@code null} query restores all events.
     *
     * <p>Uses {@link ObservableList#setAll} rather than {@code FilteredList.setPredicate()}
     * to avoid a JavaFX 21 crash caused by predicate changes firing during ComboBox
     * click-dispatch while {@code ReadOnlyUnbackedObservableList} still holds a stale size.
     *
     * @param query the text typed by the user; may be {@code null} or blank
     */
    public void filterEvents(String query) {
        if (query == null || query.isBlank()) {
            displayedEvents.setAll(allEvents);
        } else {
            String lower = query.toLowerCase();
            displayedEvents.setAll(
                allEvents.stream()
                    .filter(e -> e.getName() != null && e.getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList())
            );
        }
    }

    /**
     * Returns the selected event property.
     *
     * <p>Bound to the ComboBox selection model so that downstream controllers
     * (seat-map POS) can observe changes without querying the
     * ComboBox directly.
     *
     * @return the selectedEvent property; never {@code null}
     */
    public ObjectProperty<Event> selectedEventProperty() {
        return selectedEvent;
    }

    /**
     * Returns a read-only view of the purchase-enabled property.
     *
     * <p>This property is bound to {@link DatabaseHealthMonitor#connectedProperty()}.
     * It becomes {@code false} automatically when the database goes offline
     * (fail-safe mode) and re-enables when connectivity is restored.
     *
     * @return the purchaseEnabled property; never {@code null}
     */
    public ReadOnlyBooleanProperty purchaseEnabledProperty() {
        return purchaseEnabled.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty databaseHealthyProperty() {
        return databaseHealthy.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty availableSeatCountProperty() {
        return availableSeatCount.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<SystemHealthState> systemHealthStateProperty() {
        return systemHealthState.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty selectedEventTextProperty() {
        return selectedEventText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty availableSeatCountTextProperty() {
        return availableSeatCountText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty boothIdTextProperty() {
        return boothIdText.getReadOnlyProperty();
    }
    public ReadOnlyStringProperty databaseStatusTextProperty() {
        return databaseStatusText.getReadOnlyProperty();
    }
    public ReadOnlyStringProperty systemHealthBadgeTextProperty() {
        return systemHealthBadgeText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty systemHealthBannerTextProperty() {
        return systemHealthBannerText.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty systemHealthBannerVisibleProperty() {
        return systemHealthBannerVisible.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty purchaseBlockedReasonTextProperty() {
        return purchaseBlockedReasonText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty lastSyncTimestampTextProperty() {
        return lastSyncTimestampText.getReadOnlyProperty();
    }

    public void setBoothId(String boothId) {
        if (boothId == null || boothId.isBlank()) {
            boothIdText.set("Booth: Unassigned");
            return;
        }
        boothIdText.set("Booth: " + boothId.strip());
    }

    public void updateAvailableSeatCount(List<Seat> seats) {
        long availableCount = seats == null
                ? 0
                : seats.stream().filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE).count();
        availableSeatCount.set((int) availableCount);
    }

    public void markLastSyncNow() {
        lastSyncTimestampText.set("Last Sync: " + timestampSupplier.get().format(SYNC_TIMESTAMP_FORMATTER));
    }

    public void acknowledgeRestoredState() {
        if (systemHealthState.get() == SystemHealthState.RESTORED) {
            systemHealthState.set(SystemHealthState.HEALTHY);
            refreshHealthCopy(null, null);
        }
    }

    private void applyRuntimeStatusTransition(
            DatabaseHealthMonitor.RuntimeStatus previousStatus,
            DatabaseHealthMonitor.RuntimeStatus currentStatus
    ) {
        DatabaseHealthMonitor.RuntimeStatus effectiveCurrentStatus = currentStatus != null
                ? currentStatus
                : DatabaseHealthMonitor.RuntimeStatus.HEALTHY;

        if (effectiveCurrentStatus == DatabaseHealthMonitor.RuntimeStatus.HEALTHY) {
            if (previousStatus == DatabaseHealthMonitor.RuntimeStatus.FAIL_SAFE
                    || previousStatus == DatabaseHealthMonitor.RuntimeStatus.RECONNECTING) {
                systemHealthState.set(SystemHealthState.RESTORED);
            } else if (systemHealthState.get() != SystemHealthState.RESTORED) {
                systemHealthState.set(SystemHealthState.HEALTHY);
            }
        } else if (effectiveCurrentStatus == DatabaseHealthMonitor.RuntimeStatus.FAIL_SAFE) {
            systemHealthState.set(SystemHealthState.FAIL_SAFE);
        } else {
            systemHealthState.set(SystemHealthState.RECONNECTING);
        }
    }

    private void refreshHealthCopy(
            ObservableIntegerValue retryAttemptCount,
            ObservableLongValue retryIntervalSeconds
    ) {
        int attempts = retryAttemptCount != null ? retryAttemptCount.get() : 0;
        long retryInterval = retryIntervalSeconds != null ? retryIntervalSeconds.get() : 0L;

        switch (systemHealthState.get()) {
            case HEALTHY -> {
                databaseStatusText.set("DB: Connected - ACID Protected");
                systemHealthBadgeText.set("DB Connected");
                systemHealthBannerText.set("");
                systemHealthBannerVisible.set(false);
                purchaseBlockedReasonText.set("");
            }
            case FAIL_SAFE -> {
                databaseStatusText.set("DB: Offline - Sales Paused");
                systemHealthBadgeText.set("Fail-Safe Active");
                systemHealthBannerText.set("Database offline. Sales paused while TicketSync enters fail-safe mode.");
                systemHealthBannerVisible.set(true);
                purchaseBlockedReasonText.set(
                        "Sales are paused while TicketSync reconnects to the database. Retry checks run every "
                                + retryInterval + " seconds."
                );
            }
            case RECONNECTING -> {
                int displayAttempt = Math.max(attempts, 1);
                databaseStatusText.set("DB: Reconnecting - Sales Paused");
                systemHealthBadgeText.set("Reconnecting...");
                systemHealthBannerText.set(
                        "Reconnecting to the database (attempt " + displayAttempt + "). Sales remain paused."
                );
                systemHealthBannerVisible.set(true);
                purchaseBlockedReasonText.set(
                        "Sales are paused while TicketSync reconnects to the database. Retry attempt "
                                + displayAttempt + " is in progress."
                );
            }
            case RESTORED -> {
                databaseStatusText.set("DB: Connected - ACID Protected");
                systemHealthBadgeText.set("System Online");
                systemHealthBannerText.set("System Online - Sales resumed");
                systemHealthBannerVisible.set(true);
                purchaseBlockedReasonText.set("");
            }
        }
    }

    private static ObservableObjectValue<DatabaseHealthMonitor.RuntimeStatus> fallbackRuntimeStatus(
            ObservableBooleanValue databaseConnected
    ) {
        ObjectBinding<DatabaseHealthMonitor.RuntimeStatus> binding = Bindings.createObjectBinding(
                () -> databaseConnected.get()
                        ? DatabaseHealthMonitor.RuntimeStatus.HEALTHY
                        : DatabaseHealthMonitor.RuntimeStatus.FAIL_SAFE,
                databaseConnected
        );
        return binding;
    }

    private static ObservableIntegerValue fallbackRetryAttemptCount(ObservableBooleanValue databaseConnected) {
        IntegerBinding binding = Bindings.createIntegerBinding(
                () -> databaseConnected.get() ? 0 : 1,
                databaseConnected
        );
        return binding;
    }

    private static ObservableLongValue fallbackRetryIntervalSeconds(ObservableBooleanValue databaseConnected) {
        LongBinding binding = Bindings.createLongBinding(
                () -> databaseConnected.get() ? 30L : 10L,
                databaseConnected
        );
        return binding;
    }

}
