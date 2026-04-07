package com.ticketsync.viewmodel;

import com.ticketsync.model.Event;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
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

    private final ObservableList<Event> allEvents = FXCollections.observableArrayList();
    private final ObservableList<Event> displayedEvents = FXCollections.observableArrayList();
    private final ObjectProperty<Event> selectedEvent = new SimpleObjectProperty<>(null);

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

}
