package com.ticketsync.util;

import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Objects;

/**
 * Clase de utilidad para aplicar el tema AtlantaFX y el CSS de la aplicación a instancias
 * de {@link Alert} y {@link Dialog} de JavaFX.
 *
 * <p>Todas las sobrecargas agregan la hoja de estilos de la aplicación y la clase de estilo
 * {@code app-dialog-pane} al panel de diálogo destino, asegurando un tema consistente
 * en todos los diálogos modales.
 *

 * @since 1.0
 */
public final class DialogThemeHelper {

    private static final String DIALOG_PANE_CLASS = "app-dialog-pane";

    private DialogThemeHelper() {
    }

    /**
     * Aplica el tema de la aplicación a un {@link Dialog}.
     *
     * @param <T>    el tipo de resultado del diálogo
     * @param dialog el diálogo al que aplicar el tema; no debe ser null
     * @return la misma instancia del diálogo, para encadenamiento fluido
     * @throws NullPointerException si {@code dialog} es null
     */
    public static <T extends Dialog<?>> T apply(T dialog) {
        Objects.requireNonNull(dialog, "dialog must not be null");
        apply(dialog.getDialogPane());
        return dialog;
    }

    /**
     * Aplica el tema de la aplicación a un {@link Alert}.
     *
     * @param alert la alerta a la que aplicar el tema; no debe ser null
     * @return la misma instancia de la alerta, para encadenamiento fluido
     * @throws NullPointerException si {@code alert} es null
     */
    public static Alert apply(Alert alert) {
        return apply((Dialog<?>) alert) instanceof Alert themedAlert ? themedAlert : alert;
    }

    /**
     * Aplica el tema de la aplicación directamente a un {@link DialogPane}.
     *
     * @param dialogPane el panel de diálogo al que aplicar el tema; no debe ser null
     * @throws NullPointerException si {@code dialogPane} es null
     */
    public static void apply(DialogPane dialogPane) {
        Objects.requireNonNull(dialogPane, "dialogPane must not be null");
        apply(dialogPane.getStylesheets(), dialogPane.getStyleClass());
    }

    static void apply(ObservableList<String> stylesheets, ObservableList<String> styleClasses) {
        AppTheme.applyStylesheet(stylesheets);
        if (!styleClasses.contains(DIALOG_PANE_CLASS)) {
            styleClasses.add(DIALOG_PANE_CLASS);
        }
    }
}
