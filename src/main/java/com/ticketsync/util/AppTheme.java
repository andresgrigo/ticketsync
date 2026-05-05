package com.ticketsync.util;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.net.URL;
import java.util.Objects;

/**
 * Clase de utilidad para aplicar la hoja de estilos compartida de la aplicación TicketSync a nodos JavaFX.
 *
 * <p>Todas las sobrecargas públicas de {@code apply} agregan el CSS de la aplicación a la lista
 * de hojas de estilos del destino si aún no está presente, habilitando un estilo consistente
 * en escenas, diálogos y nodos padre independientes.
 *

 * @since 1.0
 */
public final class AppTheme {

    private static final String STYLESHEET_RESOURCE = "/com/ticketsync/application.css";

    private AppTheme() {
    }

    /**
     * Devuelve la cadena URL externa del recurso CSS compartido de la aplicación.
     *
     * @return URL en forma externa de {@code /com/ticketsync/application.css}
     * @throws IllegalStateException si el recurso CSS no se puede encontrar en el classpath
     */
    public static String stylesheetUrl() {
        URL resource = AppTheme.class.getResource(STYLESHEET_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Shared stylesheet not found: " + STYLESHEET_RESOURCE);
        }
        return resource.toExternalForm();
    }

    /**
     * Aplica la hoja de estilos compartida a una {@link javafx.scene.Scene}.
     *
     * @param scene la escena destino; no debe ser null
     * @throws NullPointerException si {@code scene} es null
     */
    public static void apply(Scene scene) {
        Objects.requireNonNull(scene, "scene must not be null");
        applyStylesheet(scene.getStylesheets());
    }

    /**
     * Aplica la hoja de estilos compartida a un nodo {@link Parent}.
     *
     * @param parent el nodo padre destino; no debe ser null
     * @throws NullPointerException si {@code parent} es null
     */
    public static void apply(Parent parent) {
        Objects.requireNonNull(parent, "parent must not be null");
        applyStylesheet(parent.getStylesheets());
    }

    /**
     * Aplica la hoja de estilos compartida a un {@link DialogPane}.
     *
     * @param dialogPane el panel de diálogo destino; no debe ser null
     * @throws NullPointerException si {@code dialogPane} es null
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
