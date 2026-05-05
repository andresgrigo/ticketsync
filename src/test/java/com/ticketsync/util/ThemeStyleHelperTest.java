package com.ticketsync.util;

import com.ticketsync.viewmodel.PosViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThemeStyleHelperTest {

    @Test
    void healthStateClass_mapsKnownSystemHealthStates() {
        assertEquals("health-healthy",
                ThemeStyleHelper.healthStateClass(PosViewModel.SystemHealthState.HEALTHY));
        assertEquals("health-healthy",
                ThemeStyleHelper.healthStateClass(PosViewModel.SystemHealthState.RESTORED));
        assertEquals("health-reconnecting",
                ThemeStyleHelper.healthStateClass(PosViewModel.SystemHealthState.RECONNECTING));
        assertEquals("health-fail-safe",
                ThemeStyleHelper.healthStateClass(PosViewModel.SystemHealthState.FAIL_SAFE));
    }

    @Test
    void statusClass_mappingsCoverEventSeatAndCountdownStates() {
        assertEquals("status-active", ThemeStyleHelper.eventStatusClass(true));
        assertEquals("status-inactive", ThemeStyleHelper.eventStatusClass(false));
        assertEquals("status-available", ThemeStyleHelper.seatStatusClass("AVAILABLE"));
        assertEquals("status-sold", ThemeStyleHelper.seatStatusClass("SOLD"));
        assertEquals("status-reserved", ThemeStyleHelper.seatStatusClass("RESERVED"));
        assertEquals("status-disabled", ThemeStyleHelper.seatStatusClass("DISABLED"));
        assertNull(ThemeStyleHelper.countdownStateClass(false));
        assertEquals("countdown-warning", ThemeStyleHelper.countdownStateClass(true));
    }

    @Test
    void applyManagedStateClass_keepsBaseClassAndSwapsPreviousState() {
        ObservableList<String> styleClasses = FXCollections.observableArrayList(
                "label",
                "system-health-badge",
                "health-healthy"
        );

        ThemeStyleHelper.applyManagedStateClass(
                styleClasses,
                "system-health-badge",
                ThemeStyleHelper.HEALTH_STATE_CLASSES,
                "health-fail-safe"
        );

        assertEquals(List.of("label", "system-health-badge", "health-fail-safe"), styleClasses);
        assertFalse(styleClasses.contains("health-healthy"));
    }
}
