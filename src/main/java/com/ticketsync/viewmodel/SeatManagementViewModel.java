package com.ticketsync.viewmodel;

import com.ticketsync.model.Seat;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Estado de la capa de presentación para el editor de distribución de asientos
 * de la pestaña Asientos del Panel de Administración.
 *
 * <p>Mantiene una {@link ObservableList} de objetos {@link Seat} que el
 * {@code AdminDashboardController} enlaza a su {@code TableView} de asientos.
 * Los cambios en la lista se reflejan automáticamente en la UI mediante
 * los enlaces de propiedades de JavaFX.
 *
 * <p>Esta clase no tiene referencia a controles de la UI de JavaFX y por tanto
 * puede ser probada sin inicializar el toolkit de JavaFX.
 */
public class SeatManagementViewModel {

    /** Crea un nuevo {@code SeatManagementViewModel} con una lista de asientos vacía. */
    public SeatManagementViewModel() {
    }

    private final ObservableList<Seat> seats= FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    /**
     * Retorna la lista observable de asientos mostrados en la tabla.
     *
     * @return la lista observable de asientos; nunca {@code null}
     */
    public ObservableList<Seat> seatsProperty() {
        return seats;
    }

    /**
     * Retorna la propiedad indicadora de carga.
     *
     * @return la propiedad loading
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Reemplaza el contenido de la lista de asientos con la lista proporcionada.
     *
     * @param newSeats la nueva lista de asientos; puede ser {@code null} (tratado como vacío)
     */
    public void setSeats(List<Seat> newSeats) {
        seats.clear();
        if (newSeats != null) {
            seats.addAll(newSeats);
        }
    }
}
