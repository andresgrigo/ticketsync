package com.ticketsync.util;

import com.ticketsync.model.SeatStatus;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemePaletteTest {

    @Test
    void seatFill_mapsVendorSeatVisualStates() {
        assertEquals(Color.web("#4CAF50"),
                ThemePalette.seatFill(ThemePalette.SeatVisualTone.AVAILABLE));
        assertEquals(Color.web("#FDD835"),
                ThemePalette.seatFill(ThemePalette.SeatVisualTone.SELECTED));
        assertEquals(Color.web("#FF9800"),
                ThemePalette.seatFill(ThemePalette.SeatVisualTone.RESERVED));
        assertEquals(Color.web("#F44336"),
                ThemePalette.seatFill(ThemePalette.SeatVisualTone.SOLD));
        assertEquals(Color.web("#9E9E9E"),
                ThemePalette.seatFill(ThemePalette.SeatVisualTone.DISABLED));
    }

    @Test
    void seatFill_mapsAdminSeatStatuses() {
        assertEquals(Color.web("#4CAF50"), ThemePalette.seatFill(SeatStatus.AVAILABLE));
        assertEquals(Color.web("#F44336"), ThemePalette.seatFill(SeatStatus.SOLD));
        assertEquals(Color.web("#FF9800"), ThemePalette.seatFill(SeatStatus.RESERVED));
        assertEquals(Color.web("#9E9E9E"), ThemePalette.seatFill(SeatStatus.DISABLED));
    }

    @Test
    void palette_exposesSharedCanvasSupportingColors() {
        assertEquals(Color.web("#37474F"), ThemePalette.seatBorder());
        assertEquals(Color.web("#1565C0"), ThemePalette.focusRing());
        assertEquals(Color.web("#212121"), ThemePalette.headingText());
        assertEquals(Color.web("#616161"), ThemePalette.metaText());
        assertEquals(Color.WHITE, ThemePalette.seatLabelText());
        assertEquals(Color.GRAY, ThemePalette.placeholderText());
        assertEquals(Color.WHITE, ThemePalette.selectionOutline());
    }
}
