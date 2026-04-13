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

    public SeatMapViewModel(SeatLoader seatLoader, ZoneLoader zoneLoader, Consumer<Runnable> uiRunner) {
        this.seatLoader = Objects.requireNonNull(seatLoader, "seatLoader must not be null");
        this.zoneLoader = Objects.requireNonNull(zoneLoader, "zoneLoader must not be null");
        this.uiRunner = Objects.requireNonNull(uiRunner, "uiRunner must not be null");
    }

    public ObservableList<Seat> seatsProperty() {
        return readOnlySeats;
    }

    public ObservableList<Seat> renderedSeatsProperty() {
        return readOnlyRenderedSeats;
    }

    public ObservableList<Zone> zonesProperty() {
        return readOnlyZones;
    }

    public ObservableSet<Integer> selectedSeatIdsProperty() {
        return readOnlySelectedSeatIds;
    }

    public ReadOnlyObjectProperty<Integer> focusedSeatIdProperty() {
        return focusedSeatId.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty focusedSeatIndexProperty() {
        return focusedSeatIndex.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading.getReadOnlyProperty();
    }

    public Zone getZone(int zoneId) {
        return zoneById.get(zoneId);
    }

    public boolean isSeatSelected(int seatId) {
        return selectedSeatIds.contains(seatId);
    }

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

    public void clearSelection() {
        uiRunner.accept(selectedSeatIds::clear);
    }

    public void showAvailableSeatsInZone(int zoneId) {
        if (zoneId <= 0) {
            throw new IllegalArgumentException("zoneId must be positive");
        }
        uiRunner.accept(() -> {
            recoveryFilterZoneId = zoneId;
            refreshRenderedSeats();
        });
    }

    public void clearRecoveryFilter() {
        uiRunner.accept(() -> {
            recoveryFilterZoneId = null;
            refreshRenderedSeats();
        });
    }

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

    @FunctionalInterface
    public interface SeatLoader {
        List<Seat> load(int eventId) throws SQLException;
    }

    @FunctionalInterface
    public interface ZoneLoader {
        List<Zone> load(int eventId) throws SQLException;
    }
}
