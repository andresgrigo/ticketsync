package com.ticketsync.viewmodel;

import com.ticketsync.model.Seat;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Presentation-layer state for the seat layout editor within the Admin
 * Dashboard Seating tab.
 *
 * <p>Holds an {@link ObservableList} of {@link Seat} objects that the
 * {@code AdminDashboardController} binds to its seats {@code TableView}.
 * Changes to the list are automatically reflected in the UI through
 * JavaFX property bindings.
 *
 * <p>This class has no reference to JavaFX UI controls and can therefore
 * be tested without initialising the JavaFX toolkit.
 */
public class SeatManagementViewModel {

    private final ObservableList<Seat> seats = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /**
     * Returns the observable list of seats displayed in the table.
     *
     * @return the observable seats list; never {@code null}
     */
    public ObservableList<Seat> seatsProperty() {
        return seats;
    }

    /**
     * Returns the loading flag property.
     *
     * @return the loading property
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Replaces the contents of the seats list with the supplied list.
     *
     * @param newSeats the new list of seats; may be {@code null} (treated as empty)
     */
    public void setSeats(List<Seat> newSeats) {
        seats.clear();
        if (newSeats != null) {
            seats.addAll(newSeats);
        }
    }
}
