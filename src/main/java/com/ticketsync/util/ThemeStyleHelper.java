package com.ticketsync.util;

import com.ticketsync.viewmodel.PosViewModel;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Clase de utilidad que proporciona constantes de clases de estilo CSS y métodos auxiliares
 * para aplicar clases de estado temadas a nodos JavaFX.
 *
 * <p>Todos los métodos son sin estado; esta clase no puede ser instanciada.
 */
public final class ThemeStyleHelper {

    /**
     * Todas las clases CSS mutuamente excluyentes que representan
     * los valores de {@link com.ticketsync.viewmodel.PosViewModel.SystemHealthState}.
     */
    public static final List<String> HEALTH_STATE_CLASSES = List.of(
            "health-healthy",
            "health-reconnecting",
            "health-fail-safe"
    );

    /**
     * Todas las clases CSS mutuamente excluyentes que representan los valores de estado
     * de eventos o asientos ({@code active}, {@code inactive}, {@code available}, {@code sold},
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
     * Todas las clases CSS mutuamente excluyentes usadas para indicar el estado de advertencia de cuenta regresiva.
     */
    public static final List<String> COUNTDOWN_STATE_CLASSES = List.of("countdown-warning");

    private ThemeStyleHelper() {
    }

    /**
     * Devuelve la clase CSS que representa el estado de salud del sistema dado.
     *
     * @param state el estado de salud actual; si es {@code null} usa {@code HEALTHY} por defecto
     * @return la cadena de clase CSS única correspondiente a {@code state}
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
     * Devuelve la clase CSS que representa si un evento está activo.
     *
     * @param active {@code true} para un evento activo; {@code false} para inactivo
     * @return {@code "status-active"} o {@code "status-inactive"}
     */
    public static String eventStatusClass(boolean active) {
        return active ? "status-active" : "status-inactive";
    }

    /**
     * Mapea una cadena de estado de asiento a su clase CSS.
     *
     * @param status estado del asiento sin distinguir mayúsculas (ej., {@code "AVAILABLE"}, {@code "SOLD"})
     * @return la clase CSS correspondiente, o {@code null} si {@code status} está en blanco o es desconocido
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
     * Devuelve la clase CSS para el estado de advertencia de cuenta regresiva.
     *
     * @param warning {@code true} si la cuenta regresiva está en estado de advertencia
     * @return {@code "countdown-warning"} cuando {@code warning} es {@code true}; de lo contrario {@code null}
     */
    public static String countdownStateClass(boolean warning) {
        return warning ? "countdown-warning" : null;
    }

    /**
     * Reemplaza atómicamente todas las clases de estado gestionadas en la lista de clases de estilo de un nodo.
     *
     * <p>Asegura que la {@code baseClass} esté presente, elimina todas las clases en
     * {@code managedStateClasses}, luego agrega {@code activeStateClass} si no está en blanco.
     *
     * @param styleClasses       la lista {@link ObservableList} de clases CSS del nodo; no debe ser null
     * @param baseClass          clase CSS base permanente a garantizar; no debe ser null
     * @param managedStateClasses todas las clases en el conjunto mutuamente excluyente a limpiar; no debe ser null
     * @param activeStateClass   la clase única a aplicar para el estado actual; puede ser null o estar en blanco
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
