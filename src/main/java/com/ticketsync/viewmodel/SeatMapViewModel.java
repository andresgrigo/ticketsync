package com.ticketsync.viewmodel;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import com.ticketsync.service.SeatService;
import com.ticketsync.service.ZoneService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Reusable presentation state for the POS seat-map canvas.
 *
 * <p>The view-model owns an observable seat snapshot, local yellow selection
 * state, keyboard focus state, and zone metadata. It deliberately does not
 * persist seat selection; this view-model only maintains local UI state while later
 * POS flows wire locking and purchase flow.
 */
public class SeatMapViewModel {

    private final ObservableList<Seat> seats = FXCollections.observableArrayList();
    private final ObservableList<Seat> readOnlySeats = FXCollections.unmodifiableObservableList(seats);
    private final ObservableList<Seat> renderedSeats = FXCollections.observableArrayList();
    private final ObservableList<Seat> readOnlyRenderedSeats =
            FXCollections.unmodifiableObservableList(renderedSeats);
    private final ObservableList<Zone> zones = FXCollections.observableArrayList();
    private final ObservableList<Zone> readOnlyZones = FXCollections.unmodifiableObservableList(zones);
    private final ObservableSet<Integer> selectedSeatIds = FXCollections.observableSet();
    private final ObservableSet<Integer> readOnlySelectedSeatIds =
            FXCollections.unmodifiableObservableSet(selectedSeatIds);
    private final ObservableMap<Integer, Zone> zoneById = FXCollections.observableMap(new LinkedHashMap<>());

    private final ReadOnlyObjectWrapper<Integer> focusedSeatId = new ReadOnlyObjectWrapper<>(null);
    private final ReadOnlyIntegerWrapper focusedSeatIndex = new ReadOnlyIntegerWrapper(-1);
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(false);

    private final SeatLoader seatLoader;
    private final ZoneLoader zoneLoader;
    private final Consumer<Runnable> uiRunner;
    private Integer recoveryFilterZoneId;

    /**
     * Creates a production-ready view-model wired to {@link SeatService} and {@link ZoneService}.
     *
     * <p>Loaded data is delivered to the FX thread via {@link Platform#runLater(Runnable)}.
     */
    public SeatMapViewModel() {
        this(
                new SeatService()::getSeatsForEvent,
                new ZoneService()::getZonesByEvent,
                runnable -> {
                    if (Platform.isFxApplicationThread()) {
                        runnable.run();
                    } else {
                        Platform.runLater(runnable);
                    }
                }
        );
    }

    /**
     * Creates a view-model with explicitly injected collaborators (primarily for testing).
     *
     * @param seatLoader strategy for loading seats given an event ID; must not be {@code null}
     * @param zoneLoader strategy for loading zones given an event ID; must not be {@code null}
     * @param uiRunner   runnable dispatcher that executes tasks on the UI thread; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SeatMapViewModel(SeatLoader seatLoader, ZoneLoader zoneLoader, Consumer<Runnable> uiRunner) {
        this.seatLoader = Objects.requireNonNull(seatLoader, "seatLoader must not be null");
        this.zoneLoader = Objects.requireNonNull(zoneLoader, "zoneLoader must not be null");
        this.uiRunner = Objects.requireNonNull(uiRunner, "uiRunner must not be null");
    }

    /**
     * Returns a read-only view of all seats loaded from the database for the current event.
     *
     * @return unmodifiable observable list; never {@code null}
     */
    public ObservableList<Seat> seatsProperty() {
        return readOnlySeats;
    }

    /**
     * Returns the subset of seats currently visible on the canvas (may be filtered by zone).
     *
     * @return unmodifiable observable list; never {@code null}
     */
    public ObservableList<Seat> renderedSeatsProperty() {
        return readOnlyRenderedSeats;
    }

    /**
     * Returns a read-only view of all zones loaded for the current event.
     *
     * @return unmodifiable observable list; never {@code null}
     */
    public ObservableList<Zone> zonesProperty() {
        return readOnlyZones;
    }

    /**
     * Returns the set of seat IDs currently selected (highlighted yellow) on the canvas.
     *
     * @return unmodifiable observable set; never {@code null}
     */
    public ObservableSet<Integer> selectedSeatIdsProperty() {
        return readOnlySelectedSeatIds;
    }

    /**
     * Returns the read-only property holding the seat ID that currently has keyboard focus,
     * or {@code null} when no seat is focused.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyObjectProperty<Integer> focusedSeatIdProperty() {
        return focusedSeatId.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property holding the list index of the currently focused seat,
     * or {@code -1} when no seat is focused.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyIntegerProperty focusedSeatIndexProperty() {
        return focusedSeatIndex.getReadOnlyProperty();
    }

    /**
     * Returns the read-only property that is {@code true} while a background load is in progress.
     *
     * @return read-only property; never {@code null}
     */
    public ReadOnlyBooleanProperty loadingProperty() {
        return loading.getReadOnlyProperty();
    }

    /**
     * Returns the zone with the given ID from the cached zone map.
     *
     * @param zoneId the zone ID to look up
     * @return the {@link Zone}, or {@code null} if not found
     */
    public Zone getZone(int zoneId) {
        return zoneById.get(zoneId);
    }

    /**
     * Returns whether the seat with the given ID is currently selected.
     *
     * @param seatId the seat ID to query
     * @return {@code true} if selected; {@code false} otherwise
     */
    public boolean isSeatSelected(int seatId) {
        return selectedSeatIds.contains(seatId);
    }

    /**
     * Loads seats and zones for the given event from the database.
     *
     * <p>Sets {@link #loadingProperty()} to {@code true} during the load and
     * delivers results to the UI thread via the configured {@code uiRunner}.
     *
     * @param eventId positive event identifier
     * @throws IllegalArgumentException if {@code eventId} is not positive
     * @throws java.sql.SQLException    if a database error occurs
     */
    public void loadEventData(int eventId) throws SQLException {
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive");
        }

        setLoading(true);
        try {
            List<Seat> loadedSeats = seatLoader.load(eventId);
            List<Zone> loadedZones = zoneLoader.load(eventId);
            uiRunner.accept(() -> applyLoadedState(loadedSeats, loadedZones));
        } catch (SQLException ex) {
            setLoading(false);
            throw ex;
        }
    }

    /**
     * Toggles the local selection state for an AVAILABLE seat.
     *
     * @param seatId the seat ID to toggle
     * @return {@code true} if the seat is selected after the operation,
     *         {@code false} if it is deselected or not selectable
     */
    public boolean toggleSeatSelection(int seatId) {
        if (!isSelectableSeat(seatId)) {
            return false;
        }

        boolean selectedAfterToggle = !selectedSeatIds.contains(seatId);
        uiRunner.accept(() -> {
            if (recoveryFilterZoneId != null) {
                recoveryFilterZoneId = null;
                refreshRenderedSeats();
            }
            if (selectedSeatIds.contains(seatId)) {
                selectedSeatIds.remove(seatId);
            } else {
                selectedSeatIds.add(seatId);
            }
            updateFocus(seatId);
        });
        return selectedAfterToggle;
    }

    /**
     * Replaces the cached seat record with an updated snapshot from the caller.
     *
     * <p>Useful for propagating status changes (e.g., RESERVED → SOLD) received
     * from the server without a full reload.
     *
     * @param updatedSeat the updated seat; must not be {@code null}
     * @return {@code true} if the seat was found and replaced; {@code false} if not present
     * @throws NullPointerException if {@code updatedSeat} is {@code null}
     */
    public boolean replaceSeat(Seat updatedSeat) {
        Objects.requireNonNull(updatedSeat, "updatedSeat must not be null");
        int index = indexOfSeat(updatedSeat.getSeatId());
        if (index < 0) {
            return false;
        }

        Seat replacement = copySeat(updatedSeat);
        uiRunner.accept(() -> {
            seats.set(index, replacement);
            refreshRenderedSeats();
            pruneSelectionForUnavailableSeats();
            reconcileFocus();
        });
        return true;
    }

    /**
     * Convenience shortcut to update only the status of a cached seat.
     *
     * @param seatId seat to update
     * @param status the new booking status; must not be {@code null}
     * @return {@code true} if the seat was found and updated; {@code false} if not present
     * @throws NullPointerException if {@code status} is {@code null}
     */
    public boolean updateSeatStatus(int seatId, SeatStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        int index = indexOfSeat(seatId);
        if (index < 0) {
            return false;
        }

        Seat current = seats.get(index);
        Seat replacement = new Seat(
                current.getSeatId(),
                current.getZoneId(),
                current.getRowNumber(),
                current.getSeatNumber(),
                status,
                current.getSaleId()
        );
        return replaceSeat(replacement);
    }

    /** Clears all locally selected seat IDs and updates the canvas. */
    public void clearSelection() {
        uiRunner.accept(selectedSeatIds::clear);
    }

    /**
     * Applies a recovery filter that limits the rendered seats to AVAILABLE seats in the given zone.
     *
     * <p>The filter is automatically cleared when the user selects a seat.
     *
     * @param zoneId the zone to filter by; must be positive
     * @throws IllegalArgumentException if {@code zoneId} is not positive
     */
    public void showAvailableSeatsInZone(int zoneId) {
        if (zoneId <= 0) {
            throw new IllegalArgumentException("zoneId must be positive");
        }
        uiRunner.accept(() -> {
            recoveryFilterZoneId = zoneId;
            refreshRenderedSeats();
        });
    }

    /** Removes the recovery zone filter and re-renders all seats for the event. */
    public void clearRecoveryFilter() {
        uiRunner.accept(() -> {
            recoveryFilterZoneId = null;
            refreshRenderedSeats();
        });
    }

    /**
     * Moves keyboard focus to the seat with the given ID.
     *
     * @param seatId the seat to focus; {@code null} clears focus
     */
    public void setFocusedSeatId(Integer seatId) {
        uiRunner.accept(() -> updateFocus(seatId));
    }

    private void applyLoadedState(List<Seat> loadedSeats, List<Zone> loadedZones) {
        List<Seat> seatCopies = new ArrayList<>(loadedSeats.size());
        for (Seat seat : loadedSeats) {
            seatCopies.add(copySeat(seat));
        }
        seatCopies.sort(Comparator
                .comparingInt(Seat::getZoneId)
                .thenComparing(Seat::getRowNumber, SeatMapViewModel::numericStringCompare)
                .thenComparing(Seat::getSeatNumber, SeatMapViewModel::numericStringCompare));

        List<Zone> zoneCopies = new ArrayList<>(loadedZones.size());
        for (Zone zone : loadedZones) {
            zoneCopies.add(copyZone(zone));
        }

        seats.setAll(seatCopies);
        zones.setAll(zoneCopies);
        zoneById.clear();
        for (Zone zone : zoneCopies) {
            zoneById.put(zone.getZoneId(), zone);
        }
        recoveryFilterZoneId = null;
        refreshRenderedSeats();
        selectedSeatIds.clear();
        if (seats.isEmpty()) {
            focusedSeatId.set(null);
            focusedSeatIndex.set(-1);
        } else {
            focusedSeatId.set(seats.getFirst().getSeatId());
            focusedSeatIndex.set(0);
        }
        loading.set(false);
    }

    private void setLoading(boolean loadingValue) {
        uiRunner.accept(() -> loading.set(loadingValue));
    }

    private void refreshRenderedSeats() {
        if (recoveryFilterZoneId == null) {
            renderedSeats.setAll(seats);
            return;
        }
        renderedSeats.setAll(seats.stream()
                .filter(seat -> seat.getZoneId() == recoveryFilterZoneId)
                .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
                .toList());
    }

    private boolean isSelectableSeat(int seatId) {
        int index = indexOfSeat(seatId);
        return index >= 0 && seats.get(index).getStatus() == SeatStatus.AVAILABLE;
    }

    private void pruneSelectionForUnavailableSeats() {
        selectedSeatIds.removeIf(this::isNoLongerAvailable);
    }

    private boolean isNoLongerAvailable(Integer seatId) {
        int index = indexOfSeat(seatId);
        return index < 0 || seats.get(index).getStatus() != SeatStatus.AVAILABLE;
    }

    private void reconcileFocus() {
        Integer currentFocusedSeatId = focusedSeatId.get();
        if (currentFocusedSeatId == null) {
            if (seats.isEmpty()) {
                focusedSeatIndex.set(-1);
            }
            return;
        }

        int index = indexOfSeat(currentFocusedSeatId);
        if (index >= 0) {
            focusedSeatIndex.set(index);
            return;
        }

        if (seats.isEmpty()) {
            focusedSeatId.set(null);
            focusedSeatIndex.set(-1);
        } else {
            focusedSeatId.set(seats.getFirst().getSeatId());
            focusedSeatIndex.set(0);
        }
    }

    private void updateFocus(Integer seatId) {
        if (seatId == null) {
            focusedSeatId.set(null);
            focusedSeatIndex.set(-1);
            return;
        }

        int index = indexOfSeat(seatId);
        if (index < 0) {
            focusedSeatId.set(null);
            focusedSeatIndex.set(-1);
            return;
        }

        focusedSeatId.set(seatId);
        focusedSeatIndex.set(index);
    }

    private int indexOfSeat(int seatId) {
        for (int index = 0; index < seats.size(); index++) {
            if (seats.get(index).getSeatId() == seatId) {
                return index;
            }
        }
        return -1;
    }

    private static Seat copySeat(Seat seat) {
        return new Seat(
                seat.getSeatId(),
                seat.getZoneId(),
                seat.getRowNumber(),
                seat.getSeatNumber(),
                seat.getStatus(),
                seat.getSaleId()
        );
    }

    private static Zone copyZone(Zone zone) {
        return new Zone(zone.getZoneId(), zone.getEventId(), zone.getName(), zone.getPrice());
    }

    private static int numericStringCompare(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException ignored) {
            return left.compareToIgnoreCase(right);
        }
    }

    /**
     * Strategy for loading seats for an event from the persistence layer.
     */
    @FunctionalInterface
    public interface SeatLoader {
        /**
         * Loads all seats for the given event.
         *
         * @param eventId the event identifier
         * @return list of seats; never {@code null}
         * @throws java.sql.SQLException if a database error occurs
         */
        List<Seat> load(int eventId) throws SQLException;
    }

    /**
     * Strategy for loading zones for an event from the persistence layer.
     */
    @FunctionalInterface
    public interface ZoneLoader {
        /**
         * Loads all zones for the given event.
         *
         * @param eventId the event identifier
         * @return list of zones; never {@code null}
         * @throws java.sql.SQLException if a database error occurs
         */
        List<Zone> load(int eventId) throws SQLException;
    }
}
