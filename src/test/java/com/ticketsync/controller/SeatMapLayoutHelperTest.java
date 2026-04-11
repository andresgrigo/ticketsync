package com.ticketsync.controller;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SeatMapLayoutHelperTest {

    @Test
    void buildLayout_ordersRowsAndSeatsNumericallyWithinZones() {
        SeatMapLayoutHelper.LayoutSnapshot layout = SeatMapLayoutHelper.buildLayout(
                List.of(
                        seat(11, 10, "A", "10", SeatStatus.AVAILABLE),
                        seat(12, 10, "A", "2", SeatStatus.AVAILABLE),
                        seat(13, 10, "A", "1", SeatStatus.AVAILABLE),
                        seat(21, 20, "2", "4", SeatStatus.SOLD),
                        seat(22, 20, "10", "1", SeatStatus.DISABLED)
                ),
                zoneMap()
        );

        assertEquals(List.of(13, 12, 11, 21, 22),
                layout.seatCells().stream().map(cell -> cell.seat().getSeatId()).toList());
        assertTrue(layout.worldWidth() > 0);
        assertTrue(layout.worldHeight() > 0);
    }

    @Test
    void findSeatAt_returnsCellForWorldCoordinates() {
        SeatMapLayoutHelper.LayoutSnapshot layout = SeatMapLayoutHelper.buildLayout(
                List.of(seat(1, 10, "A", "1", SeatStatus.AVAILABLE)),
                zoneMap()
        );

        SeatMapLayoutHelper.SeatCell cell = layout.seatCells().getFirst();
        assertEquals(1, SeatMapLayoutHelper.findSeatAt(layout, cell.worldX() + 1, cell.worldY() + 1)
                .orElseThrow()
                .seat()
                .getSeatId());
        assertTrue(SeatMapLayoutHelper.findSeatAt(layout, 0, 0).isEmpty());
    }

    @Test
    void moveFocus_navigatesAcrossLogicalGridRows() {
        SeatMapLayoutHelper.LayoutSnapshot layout = SeatMapLayoutHelper.buildLayout(
                List.of(
                        seat(1, 10, "A", "1", SeatStatus.AVAILABLE),
                        seat(2, 10, "A", "2", SeatStatus.AVAILABLE),
                        seat(3, 10, "B", "1", SeatStatus.AVAILABLE),
                        seat(4, 10, "B", "2", SeatStatus.AVAILABLE)
                ),
                zoneMap()
        );

        assertEquals(2, SeatMapLayoutHelper.moveFocus(layout, 1, SeatMapLayoutHelper.Direction.RIGHT));
        assertEquals(3, SeatMapLayoutHelper.moveFocus(layout, 1, SeatMapLayoutHelper.Direction.DOWN));
        assertEquals(2, SeatMapLayoutHelper.moveFocus(layout, 4, SeatMapLayoutHelper.Direction.UP));
    }

    @Test
    void visualStateAndTooltip_reflectLocalSelectionInsteadOfPersistedReservedStatus() {
        Seat seat = seat(1, 10, "A", "7", SeatStatus.AVAILABLE);
        Zone zone = zoneMap().get(10);

        assertEquals(SeatMapLayoutHelper.SeatVisualState.SELECTED,
                SeatMapLayoutHelper.resolveVisualState(seat, true));
        assertEquals("Zone: Floor, Row: A, Seat: 7, Price: EUR49.99, Status: SELECTED",
                SeatMapLayoutHelper.tooltipText(seat, zone, true));
    }

    @Test
    void zoomTransform_clampsBetweenHalfAndDouble() {
        SeatMapLayoutHelper.ViewportTransform zoomedIn = SeatMapLayoutHelper.zoomAround(
                new SeatMapLayoutHelper.ViewportTransform(1.95, 0, 0),
                200,
                150,
                100
        );
        SeatMapLayoutHelper.ViewportTransform zoomedOut = SeatMapLayoutHelper.zoomAround(
                new SeatMapLayoutHelper.ViewportTransform(0.55, 0, 0),
                200,
                150,
                -100
        );

        assertEquals(2.0, zoomedIn.zoom());
        assertEquals(0.5, zoomedOut.zoom());
    }

    @Test
    void layoutPreparation_forLargeVenuesStaysWithinPerformanceBudget() {
        assertTimeout(Duration.ofMillis(500), () -> {
            List<Seat> seats = new java.util.ArrayList<>();
            int id = 1;
            for (int row = 1; row <= 25; row++) {
                for (int seatNumber = 1; seatNumber <= 40; seatNumber++) {
                    seats.add(seat(id++, 10, String.valueOf(row), String.valueOf(seatNumber), SeatStatus.AVAILABLE));
                }
            }

            SeatMapLayoutHelper.LayoutSnapshot layout = SeatMapLayoutHelper.buildLayout(seats, zoneMap());
            SeatMapLayoutHelper.CanvasExtent extent = SeatMapLayoutHelper.requiredCanvasExtent(
                    layout,
                    800,
                    600,
                    new SeatMapLayoutHelper.ViewportTransform(1.0, 0, 0)
            );

            assertEquals(1000, layout.seatCells().size());
            assertTrue(extent.width() >= 800);
            assertTrue(extent.height() >= 600);
        });
    }

    private static Map<Integer, Zone> zoneMap() {
        Map<Integer, Zone> zones = new LinkedHashMap<>();
        zones.put(10, new Zone(10, 1, "Floor", new BigDecimal("49.99")));
        zones.put(20, new Zone(20, 1, "Balcony", new BigDecimal("29.50")));
        return zones;
    }

    private static Seat seat(int seatId, int zoneId, String row, String seatNumber, SeatStatus status) {
        return new Seat(seatId, zoneId, row, seatNumber, status, null);
    }
}
