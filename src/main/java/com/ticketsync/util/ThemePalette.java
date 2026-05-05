package com.ticketsync.util;

import com.ticketsync.model.SeatStatus;
import javafx.scene.paint.Color;

public final class ThemePalette {

    public enum SeatVisualTone {
        AVAILABLE,
        SELECTED,
        RESERVED,
        SOLD,
        DISABLED
    }

    private ThemePalette() {
    }

    public static Color seatFill(SeatVisualTone seatVisualTone) {
        return switch (seatVisualTone) {
            case AVAILABLE -> Color.web("#4CAF50");
            case SELECTED -> Color.web("#FDD835");
            case RESERVED -> Color.web("#FF9800");
            case SOLD -> Color.web("#F44336");
            case DISABLED -> Color.web("#9E9E9E");
        };
    }

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

    public static Color seatBorder() {
        return Color.web("#37474F");
    }

    public static Color focusRing() {
        return Color.web("#1565C0");
    }

    public static Color headingText() {
        return Color.web("#212121");
    }

    public static Color metaText() {
        return Color.web("#616161");
    }

    public static Color seatLabelText() {
        return Color.WHITE;
    }

    public static Color placeholderText() {
        return Color.GRAY;
    }

    public static Color selectionOutline() {
        return Color.WHITE;
    }
}
