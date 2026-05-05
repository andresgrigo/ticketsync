package com.ticketsync.util;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.net.URL;
import java.util.Objects;

/**
 * Utility class for applying the shared TicketSync application stylesheet to JavaFX nodes.
 *
 * <p>All public {@code apply} overloads add the application CSS to the target stylesheet
 * list if it is not already present, enabling consistent styling across scenes, dialogs,
 * and loose parent nodes.
 *

 * @since 1.0
 */
public final class AppTheme {

    private static final String STYLESHEET_RESOURCE = "/com/ticketsync/application.css";

    private AppTheme() {
    }

    /**
     * Returns the external URL string of the shared application CSS resource.
     *
     * @return external-form URL of {@code /com/ticketsync/application.css}
     * @throws IllegalStateException if the CSS resource cannot be found on the classpath
     */
    public static String stylesheetUrl() {
        URL resource = AppTheme.class.getResource(STYLESHEET_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Shared stylesheet not found: " + STYLESHEET_RESOURCE);
        }
        return resource.toExternalForm();
    }

    /**
     * Applies the shared application stylesheet to a {@link javafx.scene.Scene}.
     *
     * @param scene the target scene; must not be null
     * @throws NullPointerException if {@code scene} is null
     */
    public static void apply(Scene scene) {
        Objects.requireNonNull(scene, "scene must not be null");
        applyStylesheet(scene.getStylesheets());
    }

    /**
     * Applies the shared application stylesheet to a {@link Parent} node.
     *
     * @param parent the target parent node; must not be null
     * @throws NullPointerException if {@code parent} is null
     */
    public static void apply(Parent parent) {
        Objects.requireNonNull(parent, "parent must not be null");
        applyStylesheet(parent.getStylesheets());
    }

    /**
     * Applies the shared application stylesheet to a {@link DialogPane}.
     *
     * @param dialogPane the target dialog pane; must not be null
     * @throws NullPointerException if {@code dialogPane} is null
     */
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
