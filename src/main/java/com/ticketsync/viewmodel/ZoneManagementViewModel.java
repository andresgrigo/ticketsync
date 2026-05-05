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
 * Presentation-layer state for the zone management table within the Admin Dashboard Seating tab.
 *
 * <p>Holds an {@link ObservableList} of {@link Zone} objects that the
 * {@code AdminDashboardController} binds to its zones {@code TableView}.
 * Changes to the list are automatically reflected in the UI through JavaFX
 * property bindings.
 *
 * <p>This class has no reference to JavaFX UI controls and can therefore be
 * tested without initialising the JavaFX toolkit.
 */
public class ZoneManagementViewModel {

    /** Creates a new ZoneManagementViewModel with an empty zone list. */
    public ZoneManagementViewModel() { }

    private final ObservableList<Zone> zones = FXCollections.observableArrayList();
    private final ObjectProperty<Zone> selectedZone = new SimpleObjectProperty<>(null);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");

    /**
     * Returns the observable list of zones displayed in the table.
     *
     * @return the observable zones list; never {@code null}
     */
    public ObservableList<Zone> zonesProperty() {
        return zones;
    }

    /**
     * Returns the currently selected zone property.
     *
     * @return the selectedZone property
     */
    public ObjectProperty<Zone> selectedZoneProperty() {
        return selectedZone;
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
     * Returns the status message property.
     *
     * @return the statusMessage property
     */
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /**
     * Replaces the contents of the zones list with the supplied list.
     *
     * @param newZones the new list of zones; may be {@code null} (treated as empty)
     */
    public void setZones(List<Zone> newZones) {
        zones.clear();
        if (newZones != null) {
            zones.addAll(newZones);
        }
    }

    /**
     * Appends a zone to the observable list.
     *
     * @param zone the zone to add; must not be {@code null}
     */
    public void addZone(Zone zone) {
        zones.add(zone);
    }

    /**
     * Removes the zone with the matching {@code zoneId} from the observable list.
     *
     * @param zone the zone to remove; matched by {@code zoneId}
     */
    public void removeZone(Zone zone) {
        zones.removeIf(z -> z.getZoneId() == zone.getZoneId());
    }

    /**
     * Replaces the zone with the matching {@code zoneId} in the observable list.
     *
     * @param zone the updated zone; matched and replaced by {@code zoneId}
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
