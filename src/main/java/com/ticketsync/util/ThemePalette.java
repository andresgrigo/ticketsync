package com.ticketsync.util;

import com.ticketsync.model.SeatStatus;
import javafx.scene.paint.Color;

/**
 * Paleta de colores centralizada para la aplicación TicketSync.
 *
 * <p>Todos los colores renderizados en canvas (rellenos de asientos, bordes, etiquetas, anillos de foco, etc.)
 * están definidos aquí para que los cambios de tema solo requieran ediciones en un lugar.
 * Esta clase es un singleton de utilidad y no puede ser instanciada.
 */
public final class ThemePalette {

    /**
     * Categorías de estado visual usadas por el canvas del mapa de asientos para colorear cada celda.
     */
    public enum SeatVisualTone {
        /** El asiento está disponible para compra (verde). */
        AVAILABLE,
        /** El asiento está actualmente seleccionado por el operador (amarillo). */
        SELECTED,
        /** El asiento está temporalmente reservado por otra sesión (naranja). */
        RESERVED,
        /** El asiento ha sido vendido (rojo). */
        SOLD,
        /** El asiento está deshabilitado por un administrador (gris). */
        DISABLED
    }

    private ThemePalette() {
    }

    /**
     * Devuelve el color de relleno para una celda de asiento en el tono visual dado.
     *
     * @param seatVisualTone el estado visual a resolver; no debe ser {@code null}
     * @return el color de relleno; nunca {@code null}
     */
    public static Color seatFill(SeatVisualTone seatVisualTone) {
        return switch (seatVisualTone) {
            case AVAILABLE -> Color.web("#4CAF50");
            case SELECTED -> Color.web("#FDD835");
            case RESERVED -> Color.web("#FF9800");
            case SOLD -> Color.web("#F44336");
            case DISABLED -> Color.web("#9E9E9E");
        };
    }

    /**
     * Devuelve el color de relleno para una celda de asiento derivado de un valor {@link SeatStatus}.
     *
     * <p>Un estado {@code null} se trata como {@link SeatVisualTone#DISABLED}.
     *
     * @param seatStatus el estado de reserva del asiento; puede ser {@code null}
     * @return el color de relleno; nunca {@code null}
     */
    public static Color seatFill(SeatStatus seatStatus) {
        if (seatStatus == null) {
            return seatFill(SeatVisualTone.DISABLED);
        }

        return switch (seatStatus) {
            case AVAILABLE -> seatFill(SeatVisualTone.AVAILABLE);
            case SOLD -> seatFill(SeatVisualTone.SOLD);
            case RESERVED -> seatFill(SeatVisualTone.RESERVED);
            case DISABLED -> seatFill(SeatVisualTone.DISABLED);
        };
    }

    /**
     * Devuelve el color del borde usado para todas las celdas de asiento.
     *
     * @return el color del borde; nunca {@code null}
     */
    public static Color seatBorder() {
        return Color.web("#37474F");
    }

    /**
     * Devuelve el color del anillo de foco dibujado alrededor del asiento con foco de teclado.
     *
     * @return el color del anillo de foco; nunca {@code null}
     */
    public static Color focusRing() {
        return Color.web("#1565C0");
    }

    /**
     * Devuelve el color usado para etiquetas de encabezado de zonas y secciones en el canvas.
     *
     * @return el color del texto de encabezado; nunca {@code null}
     */
    public static Color headingText() {
        return Color.web("#212121");
    }

    /**
     * Devuelve el color usado para etiquetas de fila y otros metadatos secundarios en el canvas.
     *
     * @return el color del texto de metadatos; nunca {@code null}
     */
    public static Color metaText() {
        return Color.web("#616161");
    }

    /**
     * Devuelve el color usado para etiquetas de número de asiento dibujadas dentro de las celdas.
     *
     * @return el color del texto de etiqueta del asiento; nunca {@code null}
     */
    public static Color seatLabelText() {
        return Color.WHITE;
    }

    /**
     * Devuelve el color usado para el texto de marcador de posición mostrado cuando no hay asientos cargados.
     *
     * @return el color del texto de marcador de posición; nunca {@code null}
     */
    public static Color placeholderText() {
        return Color.GRAY;
    }

    /**
     * Devuelve el color usado para los trazos del contorno de selección en el canvas del mapa de asientos.
     *
     * @return el color del contorno de selección; nunca {@code null}
     */
    public static Color selectionOutline() {
        return Color.WHITE;
    }
}
