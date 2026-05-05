package com.ticketsync.viewmodel;

import com.ticketsync.model.Zone;
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
 * Estado de la capa de presentación para la tabla de gestión de zonas de la pestaña Asientos del Panel de Administración.
 *
 * <p>Mantiene una {@link ObservableList} de objetos {@link Zone} que el
 * {@code AdminDashboardController} enlaza a su {@code TableView} de zonas.
 * Los cambios en la lista se reflejan automáticamente en la UI mediante
 * los enlaces de propiedades de JavaFX.
 *
 * <p>Esta clase no tiene referencia a controles de la UI de JavaFX y por tanto puede ser
 * probada sin inicializar el toolkit de JavaFX.
 */
public class ZoneManagementViewModel {

    /** Crea un nuevo ZoneManagementViewModel con una lista de zonas vacía. */
    public ZoneManagementViewModel() { }

    private final ObservableList<Zone> zones = FXCollections.observableArrayList();
    private final ObjectProperty<Zone> selectedZone = new SimpleObjectProperty<>(null);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    /**
     * Retorna la lista observable de zonas mostradas en la tabla.
     *
     * @return la lista observable de zonas; nunca {@code null}
     */
    public ObservableList<Zone> zonesProperty() {
        return zones;
    }

    /**
     * Retorna la propiedad de la zona actualmente seleccionada.
     *
     * @return la propiedad selectedZone
     */
    public ObjectProperty<Zone> selectedZoneProperty() {
        return selectedZone;
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
     * Retorna la propiedad de mensaje de estado.
     *
     * @return la propiedad statusMessage
     */
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /**
     * Reemplaza el contenido de la lista de zonas con la lista proporcionada.
     *
     * @param newZones la nueva lista de zonas; puede ser {@code null} (tratado como vacío)
     */
    public void setZones(List<Zone> newZones) {
        zones.clear();
        if (newZones != null) {
            zones.addAll(newZones);
        }
    }

    /**
     * Agrega una zona a la lista observable.
     *
     * @param zone la zona a agregar; no debe ser {@code null}
     */
    public void addZone(Zone zone) {
        zones.add(zone);
    }

    /**
     * Elimina la zona con el {@code zoneId} coincidente de la lista observable.
     *
     * @param zone la zona a eliminar; igualada por {@code zoneId}
     */
    public void removeZone(Zone zone) {
        zones.removeIf(z -> z.getZoneId() == zone.getZoneId());
    }

    /**
     * Reemplaza la zona con el {@code zoneId} coincidente en la lista observable.
     *
     * @param zone la zona actualizada; igualada y reemplazada por {@code zoneId}
     */
    public void updateZone(Zone zone) {
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).getZoneId() == zone.getZoneId()) {
                zones.set(i, zone);
                return;
            }
        }
    }
}
