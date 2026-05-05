package com.ticketsync.util;

import com.ticketsync.viewmodel.PosViewModel;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility class providing CSS style-class constants and helper methods for applying
 * themed state classes to JavaFX nodes.
 *
 * <p>All methods are stateless; this class cannot be instantiated.
 */
public final class ThemeStyleHelper {

    /**
     * All mutually exclusive CSS classes that represent
     * {@link com.ticketsync.viewmodel.PosViewModel.SystemHealthState} values.
     */
    public static final List<String> HEALTH_STATE_CLASSES = List.of(
            "health-healthy",
            "health-reconnecting",
            "health-fail-safe"
    );

    /**
     * All mutually exclusive CSS classes that represent event or seat status values
     * ({@code active}, {@code inactive}, {@code available}, {@code sold},
     * {@code reserved}, {@code disabled}).
     */
    public static final List<String> STATUS_STATE_CLASSES = List.of(
            "status-active",
            "status-inactive",
            "status-available",
            "status-sold",
            "status-reserved",
            "status-disabled"
    );

    /**
     * All mutually exclusive CSS classes used to indicate countdown warning state.
     */
    public static final List<String> COUNTDOWN_STATE_CLASSES = List.of("countdown-warning");

    private ThemeStyleHelper() {
    }

    /**
     * Returns the CSS class that represents the given system health state.
     *
     * @param state the current health state; if {@code null} defaults to {@code HEALTHY}
     * @return the single CSS class string corresponding to {@code state}
     */
    public static String healthStateClass(PosViewModel.SystemHealthState state) {
        PosViewModel.SystemHealthState effectiveState =
                state != null ? state : PosViewModel.SystemHealthState.HEALTHY;
        return switch (effectiveState) {
            case HEALTHY, RESTORED -> "health-healthy";
            case RECONNECTING -> "health-reconnecting";
            case FAIL_SAFE -> "health-fail-safe";
        };
    }

    /**
     * Returns the CSS class representing whether an event is active.
     *
     * @param active {@code true} for an active event; {@code false} for inactive
     * @return {@code "status-active"} or {@code "status-inactive"}
     */
    public static String eventStatusClass(boolean active) {
        return active ? "status-active" : "status-inactive";
    }

    /**
     * Maps a seat status string to its CSS class.
     *
     * @param status case-insensitive seat status (e.g., {@code "AVAILABLE"}, {@code "SOLD"})
     * @return the corresponding CSS class, or {@code null} if {@code status} is blank or unknown
     */
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

    /**
     * Returns the CSS class for countdown warning state.
     *
     * @param warning {@code true} if the countdown is in warning state
     * @return {@code "countdown-warning"} when {@code warning} is {@code true}; otherwise {@code null}
     */
    public static String countdownStateClass(boolean warning) {
        return warning ? "countdown-warning" : null;
    }

    /**
     * Atomically replaces all managed state classes on a node's style-class list.
     *
     * <p>Ensures the {@code baseClass} is present, removes all classes in
     * {@code managedStateClasses}, then adds {@code activeStateClass} if non-blank.
     *
     * @param styleClasses       the node's live {@link ObservableList} of style classes; must not be null
     * @param baseClass          permanent base CSS class to guarantee; must not be null
     * @param managedStateClasses all classes in the mutually exclusive set to clear; must not be null
     * @param activeStateClass   the single class to apply for the current state; may be null or blank
     */
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
