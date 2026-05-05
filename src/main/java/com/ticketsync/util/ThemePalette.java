package com.ticketsync.util;

import com.ticketsync.model.SeatStatus;
import javafx.scene.paint.Color;

/**
 * Centralised colour palette for the TicketSync application.
 *
 * <p>All canvas-rendered colours (seat fills, borders, labels, focus rings, etc.)
 * are defined here so that theming changes only require edits in one place.
 * This class is a utility singleton and cannot be instantiated.
 */
public final class ThemePalette {

    /**
     * Visual state categories used by the seat-map canvas to colour each seat cell.
     */
    public enum SeatVisualTone {
        /** Seat is available for purchase (green). */
        AVAILABLE,
        /** Seat is currently selected by the operator (yellow). */
        SELECTED,
        /** Seat is temporarily reserved by another session (orange). */
        RESERVED,
        /** Seat has been sold (red). */
        SOLD,
        /** Seat is disabled by an administrator (grey). */
        DISABLED
    }

    private ThemePalette() {
    }

    /**
     * Returns the fill colour for a seat cell in the given visual tone.
     *
     * @param seatVisualTone the visual state to resolve; must not be {@code null}
     * @return the fill colour; never {@code null}
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
     * Returns the fill colour for a seat cell derived from a {@link SeatStatus} value.
     *
     * <p>A {@code null} status is treated as {@link SeatVisualTone#DISABLED}.
     *
     * @param seatStatus the booking status of the seat; may be {@code null}
     * @return the fill colour; never {@code null}
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
     * Returns the border stroke colour used for all seat cells.
     *
     * @return the border colour; never {@code null}
     */
    public static Color seatBorder() {
        return Color.web("#37474F");
    }

    /**
     * Returns the colour of the focus ring drawn around the keyboard-focused seat.
     *
     * @return the focus ring colour; never {@code null}
     */
    public static Color focusRing() {
        return Color.web("#1565C0");
    }

    /**
     * Returns the colour used for zone and section heading labels on the canvas.
     *
     * @return the heading text colour; never {@code null}
     */
    public static Color headingText() {
        return Color.web("#212121");
    }

    /**
     * Returns the colour used for row labels and other secondary metadata on the canvas.
     *
     * @return the meta text colour; never {@code null}
     */
    public static Color metaText() {
        return Color.web("#616161");
    }

    /**
     * Returns the colour used for seat number labels drawn inside seat cells.
     *
     * @return the seat label text colour; never {@code null}
     */
    public static Color seatLabelText() {
        return Color.WHITE;
    }

    /**
     * Returns the colour used for the placeholder text shown when no seats are loaded.
     *
     * @return the placeholder text colour; never {@code null}
     */
    public static Color placeholderText() {
        return Color.GRAY;
    }

    /**
     * Returns the colour used for selection outline strokes on the seat-map canvas.
     *
     * @return the selection outline colour; never {@code null}
     */
    public static Color selectionOutline() {
        return Color.WHITE;
    }
}
