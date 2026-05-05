package com.ticketsync.viewmodel;

import com.ticketsync.model.Event;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Estado de la capa de presentación para la tabla de gestión de eventos del Panel de Administración.
 *
 * <p>Mantiene una {@link ObservableList} de objetos {@link Event} que el
 * {@code AdminDashboardController} enlaza a su {@code TableView} de eventos.
 * Los cambios en la lista se reflejan automáticamente en la UI mediante
 * los enlaces de propiedades de JavaFX.
 *
 * <p>Esta clase no tiene referencia a controles de la UI de JavaFX y por tanto puede ser
 * probada sin inicializar el toolkit de JavaFX.
 */
public class EventManagementViewModel {

    /** Crea un nuevo {@code EventManagementViewModel} con una lista de eventos vacía. */
    public EventManagementViewModel() {
    }

    private final ObservableList<Event> events= FXCollections.observableArrayList();
    private final ObjectProperty<Event> selectedEvent = new SimpleObjectProperty<>(null);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    /**
     * Retorna la lista observable de eventos mostrados en la tabla.
     *
     * @return la lista observable de eventos; nunca {@code null}
     */
    public ObservableList<Event> eventsProperty() {
        return events;
    }

    /**
     * Retorna la propiedad del evento actualmente seleccionado.
     *
     * <p>Enlazada al modelo de selección del {@code TableView} para que los manejadores
     * de acciones del controlador puedan recuperar la fila seleccionada sin consultar
     * la tabla directamente.
     *
     * @return la propiedad selectedEvent
     */
    public ObjectProperty<Event> selectedEventProperty() {
        return selectedEvent;
    }

    /**
     * Retorna la propiedad indicadora de carga.
     *
     * <p>Cuando es {@code true}, una operación de carga de datos en segundo plano está en progreso.
     *
     * @return la propiedad loading
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Retorna la propiedad de mensaje de estado utilizada para mostrar texto de carga o error.
     *
     * @return la propiedad statusMessage
     */
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /**
     * Reemplaza el contenido de la lista de eventos con la lista proporcionada.
     *
     * <p>La lista existente se limpia primero para que los datos previamente cargados
     * sean descartados antes de repoblar.
     *
     * @param list la nueva lista de eventos a mostrar; no debe ser {@code null}
     */
    public void setEvents(List<Event> list) {
        events.clear();
        if (list != null) {
            events.addAll(list);
        }
    }

    /**
     * Agrega un único evento al final de la lista observable.
     *
     * @param event el evento a agregar; no debe ser {@code null}
     */
    public void addEvent(Event event) {
        events.add(event);
    }

    /**
     * Elimina el evento cuyo {@code eventId} coincide con el evento proporcionado.
     *
     * @param event el evento a eliminar; igualado por {@code eventId}
     */
    public void removeEvent(Event event) {
        events.removeIf(e -> e.getEventId() == event.getEventId());
    }

    /**
     * Reemplaza la entrada de la lista cuyo {@code eventId} coincide con el del
     * evento proporcionado con el nuevo objeto de evento.
     *
     * @param updated el evento actualizado; igualado y reemplazado por {@code eventId}
     */
    public void updateEvent(Event updated) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getEventId() == updated.getEventId()) {
                events.set(i, updated);
                return;
            }
        }
    }
}
