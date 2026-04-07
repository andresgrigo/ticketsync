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
 * Presentation-layer state for the event management table within the Admin Dashboard.
 *
 * <p>Holds an {@link ObservableList} of {@link Event} objects that the
 * {@code AdminDashboardController} binds to its events {@code TableView}.
 * Changes to the list are automatically reflected in the UI through JavaFX
 * property bindings.
 *
 * <p>This class has no reference to JavaFX UI controls and can therefore be
 * tested without initialising the JavaFX toolkit.
 */
public class EventManagementViewModel {

    private final ObservableList<Event> events = FXCollections.observableArrayList();
    private final ObjectProperty<Event> selectedEvent = new SimpleObjectProperty<>(null);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    /**
     * Returns the observable list of events displayed in the table.
     *
     * @return the observable events list; never {@code null}
     */
    public ObservableList<Event> eventsProperty() {
        return events;
    }

    /**
     * Returns the currently selected event property.
     *
     * <p>Bound to the {@code TableView} selection model so that controller
     * action handlers can retrieve the selected row without querying the
     * table directly.
     *
     * @return the selectedEvent property
     */
    public ObjectProperty<Event> selectedEventProperty() {
        return selectedEvent;
    }

    /**
     * Returns the loading flag property.
     *
     * <p>When {@code true}, a background data-loading operation is in progress.
     *
     * @return the loading property
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Returns the status message property used to display loading or error text.
     *
     * @return the statusMessage property
     */
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /**
     * Replaces the contents of the events list with the supplied list.
     *
     * <p>The existing list is cleared first so that any previously loaded
     * data is discarded before repopulating.
     *
     * @param list the new list of events to display; must not be {@code null}
     */
    public void setEvents(List<Event> list) {
        events.clear();
        if (list != null) {
            events.addAll(list);
        }
    }

    /**
     * Appends a single event to the end of the observable list.
     *
     * @param event the event to add; must not be {@code null}
     */
    public void addEvent(Event event) {
        events.add(event);
    }

    /**
     * Removes the event whose {@code eventId} matches the supplied event.
     *
     * @param event the event to remove; matched by {@code eventId}
     */
    public void removeEvent(Event event) {
        events.removeIf(e -> e.getEventId() == event.getEventId());
    }

    /**
     * Replaces the list entry whose {@code eventId} matches that of the
     * supplied event with the new event object.
     *
     * @param updated the updated event; matched and replaced by {@code eventId}
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
