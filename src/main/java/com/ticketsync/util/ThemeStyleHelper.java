package com.ticketsync.util;

import com.ticketsync.viewmodel.PosViewModel;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ThemeStyleHelper {

    public static final List<String> HEALTH_STATE_CLASSES = List.of(
            "health-healthy",
            "health-reconnecting",
            "health-fail-safe"
    );
    public static final List<String> STATUS_STATE_CLASSES = List.of(
            "status-active",
            "status-inactive",
            "status-available",
            "status-sold",
            "status-reserved",
            "status-disabled"
    );
    public static final List<String> COUNTDOWN_STATE_CLASSES = List.of("countdown-warning");

    private ThemeStyleHelper() {
    }

    public static String healthStateClass(PosViewModel.SystemHealthState state) {
        PosViewModel.SystemHealthState effectiveState =
                state != null ? state : PosViewModel.SystemHealthState.HEALTHY;
        return switch (effectiveState) {
            case HEALTHY, RESTORED -> "health-healthy";
            case RECONNECTING -> "health-reconnecting";
            case FAIL_SAFE -> "health-fail-safe";
        };
    }

    public static String eventStatusClass(boolean active) {
        return active ? "status-active" : "status-inactive";
    }

    public static String seatStatusClass(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        return switch (status.strip().toUpperCase(Locale.ROOT)) {
            case "AVAILABLE" -> "status-available";
            case "SOLD" -> "status-sold";
            case "RESERVED" -> "status-reserved";
            case "DISABLED" -> "status-disabled";
            default -> null;
        };
    }

    public static String countdownStateClass(boolean warning) {
        return warning ? "countdown-warning" : null;
    }

    public static void applyManagedStateClass(ObservableList<String> styleClasses,
            String baseClass, List<String> managedStateClasses, String activeStateClass) {
        Objects.requireNonNull(styleClasses, "styleClasses must not be null");
        Objects.requireNonNull(baseClass, "baseClass must not be null");
        Objects.requireNonNull(managedStateClasses, "managedStateClasses must not be null");

        if (!styleClasses.contains(baseClass)) {
            styleClasses.add(baseClass);
        }
        styleClasses.removeAll(managedStateClasses);
        if (activeStateClass != null && !activeStateClass.isBlank() && !styleClasses.contains(activeStateClass)) {
            styleClasses.add(activeStateClass);
        }
    }
}
