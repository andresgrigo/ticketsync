package com.ticketsync.util;

import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Objects;

/**
 * Utility class for applying the AtlantaFX theme and application CSS to JavaFX {@link Alert}
 * and {@link Dialog} instances.
 *
 * <p>All overloads add the application stylesheet and the {@code app-dialog-pane} style class
 * to the target dialog pane, ensuring consistent theming across all modal dialogs.
 *

 * @since 1.0
 */
public final class DialogThemeHelper {

    private static final String DIALOG_PANE_CLASS = "app-dialog-pane";

    private DialogThemeHelper() {
    }

    /**
     * Applies the application theme to a {@link Dialog}.
     *
     * @param <T>    the dialog's result type
     * @param dialog the dialog to theme; must not be null
     * @return the same dialog instance, for fluent chaining
     * @throws NullPointerException if {@code dialog} is null
     */
    public static <T extends Dialog<?>> T apply(T dialog) {
        Objects.requireNonNull(dialog, "dialog must not be null");
        apply(dialog.getDialogPane());
        return dialog;
    }

    /**
     * Applies the application theme to an {@link Alert}.
     *
     * @param alert the alert to theme; must not be null
     * @return the same alert instance, for fluent chaining
     * @throws NullPointerException if {@code alert} is null
     */
    public static Alert apply(Alert alert) {
        return apply((Dialog<?>) alert) instanceof Alert themedAlert ? themedAlert : alert;
    }

    /**
     * Applies the application theme to a {@link DialogPane} directly.
     *
     * @param dialogPane the dialog pane to theme; must not be null
     * @throws NullPointerException if {@code dialogPane} is null
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
