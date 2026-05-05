package com.ticketsync.util;

import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Objects;

public final class DialogThemeHelper {

    private static final String DIALOG_PANE_CLASS = "app-dialog-pane";

    private DialogThemeHelper() {
    }

    public static <T extends Dialog<?>> T apply(T dialog) {
        Objects.requireNonNull(dialog, "dialog must not be null");
        apply(dialog.getDialogPane());
        return dialog;
    }

    public static Alert apply(Alert alert) {
        return apply((Dialog<?>) alert) instanceof Alert themedAlert ? themedAlert : alert;
    }

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
