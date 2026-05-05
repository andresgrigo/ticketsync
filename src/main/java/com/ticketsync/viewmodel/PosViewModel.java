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
 * Estado de la capa de presentación para el selector de eventos del POS del Vendedor.
 *
 * <p>Mantiene la lista observable de todos los eventos activos y una
 * {@link ObservableList} mostrada a la que el ComboBox se enlaza. El filtrado se implementa
 * repoblando la lista mostrada mediante {@link #filterEvents(String)}; esto evita
 * usar {@code FilteredList.setPredicate()} que activa un error de JavaFX 21
 * ({@code ReadOnlyUnbackedObservableList.subList()} lee un tamaño desactualizado durante
 * el despacho de clic del ComboBox y lanza {@code IndexOutOfBoundsException}).
 *
 * <p>Esta clase no tiene referencia a controles de la UI de JavaFX y por tanto puede ser
 * probada sin inicializar el toolkit de JavaFX.
 *
 * <p>("Vista Principal del POS con Mapa de Asientos") consumirá
 * {@link #selectedEventProperty()} para controlar la carga de asientos. Recupere este
 * view-model del controlador mediante {@code PosController#getViewModel()}.
 */
public class PosViewModel {

    private static final DateTimeFormatter SYNC_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Representa el estado general de salud del sistema visible para el operador del POS.
     *
     * <p>Refleja {@link com.ticketsync.util.DatabaseHealthMonitor.RuntimeStatus} pero
     * agrega el estado transitorio {@link #RESTORED} utilizado para activar el banner
     * de notificación "conexión restaurada".
     */
    public enum SystemHealthState {
        /** Estado de operación normal; la base de datos es accesible y las compras están permitidas. */
        HEALTHY,
        /** Base de datos no accesible; el POS está en modo de seguridad de solo lectura / sin ventas. */
        FAIL_SAFE,
        /** El latido previo falló; el monitor está reintentando activamente la conexión. */
        RECONNECTING,
        /** Estado transitorio que indica que la conectividad acaba de restaurarse; transiciona a {@link #HEALTHY}. */
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

    /**
     * Crea un {@code PosViewModel} de producción conectado al singleton
     * {@link com.ticketsync.util.DatabaseHealthMonitor} y al reloj del sistema.
     */
    public PosViewModel() {
        this(
                DatabaseHealthMonitor.getInstance().connectedProperty(),
                DatabaseHealthMonitor.getInstance().runtimeStatusProperty(),
                DatabaseHealthMonitor.getInstance().retryAttemptCountProperty(),
                DatabaseHealthMonitor.getInstance().currentCheckIntervalSecondsProperty(),
                LocalDateTime::now
        );
    }

    /**
     * Crea un {@code PosViewModel} con un observable de conexión de base de datos personalizado y proveedor de marca de tiempo.
     *
     * <p>Usado en pruebas para proporcionar un {@code ObservableBooleanValue} controlado sin iniciar el
     * {@link com.ticketsync.util.DatabaseHealthMonitor} real.
     *
     * @param databaseConnected observable booleano que es {@code true} cuando la BD es accesible
     * @param timestampSupplier proveedor utilizado para generar cadenas de marca de tiempo de "última sincronización"
     */
    public PosViewModel(ObservableBooleanValue databaseConnected, Supplier<LocalDateTime> timestampSupplier) {
        this(
                databaseConnected,
                fallbackRuntimeStatus(databaseConnected),
                fallbackRetryAttemptCount(databaseConnected),
                fallbackRetryIntervalSeconds(databaseConnected),
                timestampSupplier
        );
    }

    /**
     * Constructor de control total utilizado en pruebas unitarias.
     *
     * @param databaseConnected    observable booleano; {@code true} cuando la BD es accesible
     * @param runtimeStatus        estado de ejecución observable del monitor de salud
     * @param retryAttemptCount    contador de reintentos observable expuesto por el monitor de salud
     * @param retryIntervalSeconds intervalo de verificación actual observable en segundos
     * @param timestampSupplier    proveedor utilizado para generar cadenas de marca de tiempo de "última sincronización"
     */
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
     * Retorna la lista de eventos mostrados a la que el {@code ComboBox} se enlaza.
     *
     * <p>Actualizada por {@link #setEvents(List)} al cargar y por
     * {@link #filterEvents(String)} cuando el usuario escribe en el campo de búsqueda.
     *
     * @return la lista de eventos mostrados; nunca {@code null}
     */
    public ObservableList<Event> getFilteredEvents() {
        return displayedEvents;
    }

    /**
     * Reemplaza la lista de respaldo con los eventos proporcionados y restablece el predicado
     * de filtro para que todos los elementos sean visibles después de una carga nueva.
     *
     * @param events la nueva lista de eventos activos; no debe ser {@code null}
     */
    public void setEvents(List<Event> events) {
        allEvents.setAll(events);
        displayedEvents.setAll(events);
    }

    /**
     * Repuebla la lista mostrada con eventos cuyo nombre contiene {@code query}
     * (sin distinción de mayúsculas). Pasar una consulta en blanco o {@code null} restaura todos los eventos.
     *
     * <p>Usa {@link ObservableList#setAll} en lugar de {@code FilteredList.setPredicate()}
     * para evitar un fallo de JavaFX 21 causado por cambios de predicado que se disparan durante el
     * despacho de clic del ComboBox mientras {@code ReadOnlyUnbackedObservableList} aún mantiene un tamaño desactualizado.
     *
     * @param query el texto escrito por el usuario; puede ser {@code null} o estar en blanco
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
     * Retorna la propiedad del evento seleccionado.
     *
     * <p>Enlazada al modelo de selección del ComboBox para que los controladores
     * descendentes (POS de mapa de asientos) puedan observar cambios sin consultar
     * el ComboBox directamente.
     *
     * @return la propiedad selectedEvent; nunca {@code null}
     */
    public ObjectProperty<Event> selectedEventProperty() {
        return selectedEvent;
    }

    /**
     * Retorna una vista de solo lectura de la propiedad de compra habilitada.
     *
     * <p>Esta propiedad está enlazada a {@link DatabaseHealthMonitor#connectedProperty()}.
     * Se convierte en {@code false} automáticamente cuando la base de datos se desconecta
     * (modo de seguridad) y se vuelve a habilitar cuando se restaura la conectividad.
     *
     * @return la propiedad purchaseEnabled; nunca {@code null}
     */
    public ReadOnlyBooleanProperty purchaseEnabledProperty() {
        return purchaseEnabled.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que indica si la base de datos está en buen estado.
     *
     * @return la propiedad databaseHealthy; nunca {@code null}
     */
    public ReadOnlyBooleanProperty databaseHealthyProperty() {
        return databaseHealthy.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene el número de asientos disponibles para el evento seleccionado.
     *
     * @return la propiedad availableSeatCount; nunca {@code null}
     */
    public ReadOnlyIntegerProperty availableSeatCountProperty() {
        return availableSeatCount.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene el estado de salud del sistema actual.
     *
     * @return la propiedad systemHealthState; nunca {@code null}
     */
    public ReadOnlyObjectProperty<SystemHealthState> systemHealthStateProperty() {
        return systemHealthState.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene el texto de visualización del evento seleccionado.
     *
     * @return la propiedad selectedEventText; nunca {@code null}
     */
    public ReadOnlyStringProperty selectedEventTextProperty() {
        return selectedEventText.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene el texto de conteo de asientos disponibles formateado.
     *
     * @return la propiedad availableSeatCountText; nunca {@code null}
     */
    public ReadOnlyStringProperty availableSeatCountTextProperty() {
        return availableSeatCountText.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene el texto de ID de cabina formateado.
     *
     * @return la propiedad boothIdText; nunca {@code null}
     */
    public ReadOnlyStringProperty boothIdTextProperty() {
        return boothIdText.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene el texto de estado de conexión de la base de datos.
     *
     * @return la propiedad databaseStatusText; nunca {@code null}
     */
    public ReadOnlyStringProperty databaseStatusTextProperty() {
        return databaseStatusText.getReadOnlyProperty();
    }
    /**
     * Retorna una propiedad de solo lectura que contiene el texto de visualización de la insignia de salud (p. ej. "HEALTHY").
     *
     * @return la propiedad systemHealthBadgeText; nunca {@code null}
     */
    public ReadOnlyStringProperty systemHealthBadgeTextProperty() {
        return systemHealthBadgeText.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene el texto del banner de salud del sistema mostrado al operador.
     *
     * @return la propiedad systemHealthBannerText; nunca {@code null}
     */
    public ReadOnlyStringProperty systemHealthBannerTextProperty() {
        return systemHealthBannerText.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que indica si el banner de salud del sistema debe mostrarse.
     *
     * @return la propiedad systemHealthBannerVisible; nunca {@code null}
     */
    public ReadOnlyBooleanProperty systemHealthBannerVisibleProperty() {
        return systemHealthBannerVisible.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene la razón legible por humanos por la que las compras están bloqueadas,
     * o una cadena vacía cuando las compras están permitidas.
     *
     * @return la propiedad purchaseBlockedReasonText; nunca {@code null}
     */
    public ReadOnlyStringProperty purchaseBlockedReasonTextProperty() {
        return purchaseBlockedReasonText.getReadOnlyProperty();
    }

    /**
     * Retorna una propiedad de solo lectura que contiene la marca de tiempo formateada de la última sincronización exitosa de asientos.
     *
     * @return la propiedad lastSyncTimestampText; nunca {@code null}
     */
    public ReadOnlyStringProperty lastSyncTimestampTextProperty() {
        return lastSyncTimestampText.getReadOnlyProperty();
    }

    /**
     * Actualiza el ID de cabina mostrado en la barra de encabezado.
     *
     * <p>Si {@code boothId} es {@code null} o está en blanco, la visualización vuelve a "Booth: Unassigned".
     *
     * @param boothId la cadena de identificador de cabina; puede ser {@code null}
     */
    public void setBoothId(String boothId) {
        if (boothId == null || boothId.isBlank()) {
            boothIdText.set("Booth: Unassigned");
            return;
        }
        boothIdText.set("Booth: " + boothId.strip());
    }

    /**
     * Actualiza el conteo de asientos disponibles a partir de una lista de asientos recién cargada.
     *
     * <p>Cuenta solo asientos con estado {@link com.ticketsync.model.SeatStatus#AVAILABLE}.
     * Una lista {@code null} se trata como cero asientos disponibles.
     *
     * @param seats la lista de asientos actual para el evento seleccionado; puede ser {@code null}
     */
    public void updateAvailableSeatCount(List<Seat> seats) {
        long availableCount = seats == null
                ? 0
                : seats.stream().filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE).count();
        availableSeatCount.set((int) availableCount);
    }

    /**
     * Actualiza el texto de marca de tiempo de "última sincronización" al instante actual proporcionado
     * por el proveedor de marca de tiempo configurado.
     */
    public void markLastSyncNow() {
        lastSyncTimestampText.set("Last Sync: " + timestampSupplier.get().format(SYNC_TIMESTAMP_FORMATTER));
    }

    /**
     * Hace la transición del estado de salud de {@link SystemHealthState#RESTORED} de vuelta a
     * {@link SystemHealthState#HEALTHY} una vez que el llamador ha mostrado el aviso de restauración.
     *
     * <p>Si el estado actual no es {@code RESTORED}, este método no hace nada.
     */
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
