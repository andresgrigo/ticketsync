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
 * Estado de presentación reutilizable para el lienzo del mapa de asientos del POS.
 *
 * <p>El view-model posee una instantánea observable de asientos, estado de selección
 * local amarillo, estado de foco de teclado y metadatos de zona. Deliberadamente no
 * persiste la selección de asientos; este view-model solo mantiene el estado local de la UI
 * mientras los flujos posteriores del POS conectan el bloqueo y el flujo de compra.
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
     * Crea un view-model listo para producción conectado a {@link SeatService} y {@link ZoneService}.
     *
     * <p>Los datos cargados se entregan al hilo FX mediante {@link Platform#runLater(Runnable)}.
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
     * Crea un view-model con colaboradores inyectados explícitamente (principalmente para pruebas).
     *
     * @param seatLoader estrategia para cargar asientos dado un ID de evento; no debe ser {@code null}
     * @param zoneLoader estrategia para cargar zonas dado un ID de evento; no debe ser {@code null}
     * @param uiRunner   despachador de ejecutables que ejecuta tareas en el hilo de UI; no debe ser {@code null}
     * @throws NullPointerException si algún argumento es {@code null}
     */
    public SeatMapViewModel(SeatLoader seatLoader, ZoneLoader zoneLoader, Consumer<Runnable> uiRunner) {
        this.seatLoader = Objects.requireNonNull(seatLoader, "seatLoader must not be null");
        this.zoneLoader = Objects.requireNonNull(zoneLoader, "zoneLoader must not be null");
        this.uiRunner = Objects.requireNonNull(uiRunner, "uiRunner must not be null");
    }

    /**
     * Retorna una vista de solo lectura de todos los asientos cargados de la base de datos para el evento actual.
     *
     * @return lista observable no modificable; nunca {@code null}
     */
    public ObservableList<Seat> seatsProperty() {
        return readOnlySeats;
    }

    /**
     * Retorna el subconjunto de asientos actualmente visibles en el lienzo (puede estar filtrado por zona).
     *
     * @return lista observable no modificable; nunca {@code null}
     */
    public ObservableList<Seat> renderedSeatsProperty() {
        return readOnlyRenderedSeats;
    }

    /**
     * Retorna una vista de solo lectura de todas las zonas cargadas para el evento actual.
     *
     * @return lista observable no modificable; nunca {@code null}
     */
    public ObservableList<Zone> zonesProperty() {
        return readOnlyZones;
    }

    /**
     * Retorna el conjunto de IDs de asientos actualmente seleccionados (resaltados en amarillo) en el lienzo.
     *
     * @return conjunto observable no modificable; nunca {@code null}
     */
    public ObservableSet<Integer> selectedSeatIdsProperty() {
        return readOnlySelectedSeatIds;
    }

    /**
     * Retorna la propiedad de solo lectura que contiene el ID del asiento que actualmente tiene el foco del teclado,
     * o {@code null} cuando ningún asiento está enfocado.
     *
     * @return propiedad de solo lectura; nunca {@code null}
     */
    public ReadOnlyObjectProperty<Integer> focusedSeatIdProperty() {
        return focusedSeatId.getReadOnlyProperty();
    }

    /**
     * Retorna la propiedad de solo lectura que contiene el índice de lista del asiento actualmente enfocado,
     * o {@code -1} cuando ningún asiento está enfocado.
     *
     * @return propiedad de solo lectura; nunca {@code null}
     */
    public ReadOnlyIntegerProperty focusedSeatIndexProperty() {
        return focusedSeatIndex.getReadOnlyProperty();
    }

    /**
     * Retorna la propiedad de solo lectura que es {@code true} mientras una carga en segundo plano está en progreso.
     *
     * @return propiedad de solo lectura; nunca {@code null}
     */
    public ReadOnlyBooleanProperty loadingProperty() {
        return loading.getReadOnlyProperty();
    }

    /**
     * Retorna la zona con el ID dado del mapa de zonas en caché.
     *
     * @param zoneId el ID de zona a buscar
     * @return la {@link Zone}, o {@code null} si no se encuentra
     */
    public Zone getZone(int zoneId) {
        return zoneById.get(zoneId);
    }

    /**
     * Retorna si el asiento con el ID dado está actualmente seleccionado.
     *
     * @param seatId el ID del asiento a consultar
     * @return {@code true} si está seleccionado; {@code false} en caso contrario
     */
    public boolean isSeatSelected(int seatId) {
        return selectedSeatIds.contains(seatId);
    }

    /**
     * Carga asientos y zonas para el evento dado desde la base de datos.
     *
     * <p>Establece {@link #loadingProperty()} en {@code true} durante la carga y
     * entrega los resultados al hilo de UI mediante el {@code uiRunner} configurado.
     *
     * @param eventId identificador de evento positivo
     * @throws IllegalArgumentException si {@code eventId} no es positivo
     * @throws java.sql.SQLException    si ocurre un error de base de datos
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
     * Alterna el estado de selección local para un asiento DISPONIBLE.
     *
     * @param seatId el ID del asiento a alternar
     * @return {@code true} si el asiento está seleccionado después de la operación,
     *         {@code false} si está deseleccionado o no es seleccionable
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
     * Reemplaza el registro de asiento en caché con una instantánea actualizada del llamador.
     *
     * <p>Útil para propagar cambios de estado (p. ej., RESERVADO → VENDIDO) recibidos
     * del servidor sin una recarga completa.
     *
     * @param updatedSeat el asiento actualizado; no debe ser {@code null}
     * @return {@code true} si el asiento fue encontrado y reemplazado; {@code false} si no está presente
     * @throws NullPointerException si {@code updatedSeat} es {@code null}
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
     * Atajo de conveniencia para actualizar solo el estado de un asiento en caché.
     *
     * @param seatId asiento a actualizar
     * @param status el nuevo estado de reserva; no debe ser {@code null}
     * @return {@code true} si el asiento fue encontrado y actualizado; {@code false} si no está presente
     * @throws NullPointerException si {@code status} es {@code null}
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

    /** Limpia todos los IDs de asientos seleccionados localmente y actualiza el lienzo. */
    public void clearSelection() {
        uiRunner.accept(selectedSeatIds::clear);
    }

    /**
     * Aplica un filtro de recuperación que limita los asientos renderizados a los asientos DISPONIBLES en la zona dada.
     *
     * <p>El filtro se limpia automáticamente cuando el usuario selecciona un asiento.
     *
     * @param zoneId la zona por la que filtrar; debe ser positivo
     * @throws IllegalArgumentException si {@code zoneId} no es positivo
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

    /** Elimina el filtro de zona de recuperación y re-renderiza todos los asientos del evento. */
    public void clearRecoveryFilter() {
        uiRunner.accept(() -> {
            recoveryFilterZoneId = null;
            refreshRenderedSeats();
        });
    }

    /**
     * Mueve el foco de teclado al asiento con el ID dado.
     *
     * @param seatId el asiento a enfocar; {@code null} limpia el foco
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
