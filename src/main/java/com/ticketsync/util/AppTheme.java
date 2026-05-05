package com.ticketsync.util;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.net.URL;
import java.util.Objects;

public final class AppTheme {

    private static final String STYLESHEET_RESOURCE = "/com/ticketsync/application.css";

    private AppTheme() {
    }

    public static String stylesheetUrl() {
        URL resource = AppTheme.class.getResource(STYLESHEET_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Shared stylesheet not found: " + STYLESHEET_RESOURCE);
        }
        return resource.toExternalForm();
    }

    public static void apply(Scene scene) {
        Objects.requireNonNull(scene, "scene must not be null");
        applyStylesheet(scene.getStylesheets());
    }

    public static void apply(Parent parent) {
        Objects.requireNonNull(parent, "parent must not be null");
        applyStylesheet(parent.getStylesheets());
    }

    public static void apply(DialogPane dialogPane) {
        Objects.requireNonNull(dialogPane, "dialogPane must not be null");
        applyStylesheet(dialogPane.getStylesheets());
    }

    static void applyStylesheet(ObservableList<String> stylesheets) {
        Objects.requireNonNull(stylesheets, "stylesheets must not be null");
        String stylesheet = stylesheetUrl();
        if (!stylesheets.contains(stylesheet)) {
            stylesheets.add(stylesheet);
        }
    }
}
