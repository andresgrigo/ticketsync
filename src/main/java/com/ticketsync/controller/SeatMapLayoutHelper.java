package com.ticketsync.controller;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class SeatMapLayoutHelper {

    static final double SEAT_SIZE = 40.0;
    static final double SEAT_ARC = 8.0;
    static final double SEAT_GAP = 6.0;
    static final double ROW_GAP = 8.0;
    static final double ZONE_GAP = 28.0;
    static final double PADDING = 16.0;
    static final double ROW_LABEL_WIDTH = 56.0;
    static final double ZONE_LABEL_HEIGHT = 24.0;
    static final double ZONE_LABEL_GAP = 8.0;
    static final double MIN_ZOOM = 0.5;
    static final double MAX_ZOOM = 2.0;
    static final double MAX_CANVAS_EXTENT = 8192.0;

    private SeatMapLayoutHelper() {
    }

    static LayoutSnapshot buildLayout(List<Seat> seats, Map<Integer, Zone> zonesById) {
        if (seats == null || seats.isEmpty()) {
            return new LayoutSnapshot(2 * PADDING, 2 * PADDING, List.of(), List.of(), Map.of(), List.of());
        }

        LinkedHashMap<Integer, List<Seat>> seatsByZone = new LinkedHashMap<>();
        if (zonesById != null) {
            for (Integer zoneId : zonesById.keySet()) {
                seatsByZone.put(zoneId, new ArrayList<>());
            }
        }
        for (Seat seat : seats) {
            seatsByZone.computeIfAbsent(seat.getZoneId(), ignored -> new ArrayList<>()).add(seat);
        }

        List<SeatCell> allCells = new ArrayList<>();
        Map<Integer, SeatCell> cellsById = new LinkedHashMap<>();
        List<List<SeatCell>> gridRows = new ArrayList<>();
        List<ZoneLayout> zoneLayouts = new ArrayList<>();

        double yOffset = PADDING;
        double worldWidth = 2 * PADDING + ROW_LABEL_WIDTH;
        int gridRowIndex = 0;

        for (Map.Entry<Integer, List<Seat>> zoneEntry : seatsByZone.entrySet()) {
            if (zoneEntry.getValue().isEmpty()) {
                continue;
            }

            Zone zone = zonesById != null ? zonesById.get(zoneEntry.getKey()) : null;
            String zoneLabel = zone != null ? zone.getName() : "Zone " + zoneEntry.getKey();
            double headerY = yOffset;
            yOffset += ZONE_LABEL_HEIGHT + ZONE_LABEL_GAP;

            Map<String, List<Seat>> rowMap = new LinkedHashMap<>();
            zoneEntry.getValue().stream()
                    .sorted(Comparator
                            .comparing(Seat::getRowNumber, SeatMapLayoutHelper::numericStringCompare)
                            .thenComparing(Seat::getSeatNumber, SeatMapLayoutHelper::numericStringCompare))
                    .forEach(seat -> rowMap.computeIfAbsent(seat.getRowNumber(), ignored -> new ArrayList<>()).add(seat));

            List<RowLayout> rowLayouts = new ArrayList<>();
            for (Map.Entry<String, List<Seat>> rowEntry : rowMap.entrySet()) {
                List<Seat> rowSeats = new ArrayList<>(rowEntry.getValue());
                rowSeats.sort((left, right) -> numericStringCompare(left.getSeatNumber(), right.getSeatNumber()));

                List<SeatCell> rowCells = new ArrayList<>();
                double seatStartX = PADDING + ROW_LABEL_WIDTH;
                double rowWidth = rowSeats.isEmpty() ? seatStartX : seatStartX
                        + rowSeats.size() * SEAT_SIZE + Math.max(0, rowSeats.size() - 1) * SEAT_GAP;
                worldWidth = Math.max(worldWidth, rowWidth + PADDING);

                for (int columnIndex = 0; columnIndex < rowSeats.size(); columnIndex++) {
                    Seat seat = rowSeats.get(columnIndex);
                    double seatX = seatStartX + columnIndex * (SEAT_SIZE + SEAT_GAP);
                    SeatCell cell = new SeatCell(seat, zone, seatX, yOffset, gridRowIndex, columnIndex);
                    rowCells.add(cell);
                    allCells.add(cell);
                    cellsById.put(seat.getSeatId(), cell);
                }

                rowLayouts.add(new RowLayout(rowEntry.getKey(), yOffset, rowCells));
                gridRows.add(rowCells);
                gridRowIndex++;
                yOffset += SEAT_SIZE + ROW_GAP;
            }

            zoneLayouts.add(new ZoneLayout(zone, zoneLabel, headerY, rowLayouts));
            yOffset += ZONE_GAP;
        }

        return new LayoutSnapshot(worldWidth, yOffset + PADDING, zoneLayouts, allCells, cellsById, gridRows);
    }

    static Optional<SeatCell> findSeatAt(LayoutSnapshot layout, double worldX, double worldY) {
        for (SeatCell cell : layout.seatCells()) {
            if (worldX >= cell.worldX() && worldX <= cell.worldX() + SEAT_SIZE
                    && worldY >= cell.worldY() && worldY <= cell.worldY() + SEAT_SIZE) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    static Integer moveFocus(LayoutSnapshot layout, Integer currentSeatId, Direction direction) {
        if (layout.seatCells().isEmpty()) {
            return null;
        }

        SeatCell currentCell = currentSeatId == null ? null : layout.seatCellById().get(currentSeatId);
        if (currentCell == null) {
            return layout.seatCells().getFirst().seat().getSeatId();
        }

        return switch (direction) {
            case LEFT -> seatIdAt(layout.gridRows(), currentCell.gridRow(), currentCell.gridColumn() - 1)
                    .orElse(currentSeatId);
            case RIGHT -> seatIdAt(layout.gridRows(), currentCell.gridRow(), currentCell.gridColumn() + 1)
                    .orElse(currentSeatId);
            case UP -> moveVertical(layout.gridRows(), currentCell, -1, currentSeatId);
            case DOWN -> moveVertical(layout.gridRows(), currentCell, 1, currentSeatId);
        };
    }

    static SeatVisualState resolveVisualState(Seat seat, boolean selected) {
        if (selected && seat.getStatus() == SeatStatus.AVAILABLE) {
            return SeatVisualState.SELECTED;
        }
        return switch (seat.getStatus()) {
            case AVAILABLE -> SeatVisualState.AVAILABLE;
            case SOLD -> SeatVisualState.SOLD;
            case RESERVED -> SeatVisualState.RESERVED;
            case DISABLED -> SeatVisualState.DISABLED;
        };
    }

    static String tooltipText(Seat seat, Zone zone, boolean selected) {
        String zoneName = zone != null && zone.getName() != null ? zone.getName() : "Zone " + seat.getZoneId();
        String rowLabel = seat.getRowNumber() != null ? seat.getRowNumber() : "?";
        String seatLabel = seat.getSeatNumber() != null ? seat.getSeatNumber() : "?";
        String priceLabel = zone != null && zone.getPrice() != null ? zone.getPrice().toPlainString() : "?";
        return "Zone: " + zoneName
                + ", Row: " + rowLabel
                + ", Seat: " + seatLabel
                + ", Price: EUR" + priceLabel
                + ", Status: " + resolveVisualState(seat, selected).name();
    }

    static ViewportTransform zoomAround(ViewportTransform current, double mouseX, double mouseY, double deltaY) {
        if (deltaY == 0) {
            return current;
        }

        double scaleFactor = deltaY > 0 ? 1.1 : 0.9;
        double newZoom = clamp(current.zoom() * scaleFactor, MIN_ZOOM, MAX_ZOOM);
        if (Double.compare(newZoom, current.zoom()) == 0) {
            return current;
        }

        double worldX = (mouseX - current.panX()) / current.zoom();
        double worldY = (mouseY - current.panY()) / current.zoom();
        double newPanX = mouseX - worldX * newZoom;
        double newPanY = mouseY - worldY * newZoom;
        return new ViewportTransform(newZoom, newPanX, newPanY);
    }

    static CanvasExtent requiredCanvasExtent(
            LayoutSnapshot layout,
            double viewportWidth,
            double viewportHeight,
            ViewportTransform transform
    ) {
        double width = Math.min(
                Math.max(viewportWidth, layout.worldWidth() * transform.zoom() + Math.abs(transform.panX()) + PADDING),
                MAX_CANVAS_EXTENT
        );
        double height = Math.min(
                Math.max(viewportHeight, layout.worldHeight() * transform.zoom() + Math.abs(transform.panY()) + PADDING),
                MAX_CANVAS_EXTENT
        );
        return new CanvasExtent(width, height);
    }

    private static Optional<Integer> seatIdAt(List<List<SeatCell>> rows, int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return Optional.empty();
        }
        List<SeatCell> row = rows.get(rowIndex);
        if (columnIndex < 0 || columnIndex >= row.size()) {
            return Optional.empty();
        }
        return Optional.of(row.get(columnIndex).seat().getSeatId());
    }

    private static Integer moveVertical(
            List<List<SeatCell>> rows,
            SeatCell currentCell,
            int step,
            Integer fallbackSeatId
    ) {
        for (int rowIndex = currentCell.gridRow() + step; rowIndex >= 0 && rowIndex < rows.size(); rowIndex += step) {
            List<SeatCell> row = rows.get(rowIndex);
            if (row.isEmpty()) {
                continue;
            }
            SeatCell nearest = row.stream()
                    .min(Comparator.comparingInt(cell -> Math.abs(cell.gridColumn() - currentCell.gridColumn())))
                    .orElse(null);
            if (nearest != null) {
                return nearest.seat().getSeatId();
            }
        }
        return fallbackSeatId;
    }

    private static int numericStringCompare(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException ignored) {
            return left.compareToIgnoreCase(right);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    enum SeatVisualState {
        AVAILABLE,
        SELECTED,
        RESERVED,
        SOLD,
        DISABLED
    }

    record ViewportTransform(double zoom, double panX, double panY) {
        ViewportTransform {
            zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        }
    }

    record CanvasExtent(double width, double height) {
    }

    record SeatCell(Seat seat, Zone zone, double worldX, double worldY, int gridRow, int gridColumn) {
        SeatCell {
            Objects.requireNonNull(seat, "seat must not be null");
        }
    }

    record RowLayout(String rowLabel, double topY, List<SeatCell> seatCells) {
        RowLayout {
            seatCells = List.copyOf(seatCells);
        }
    }

    record ZoneLayout(Zone zone, String zoneLabel, double headerY, List<RowLayout> rows) {
        ZoneLayout {
            rows = List.copyOf(rows);
        }
    }

    record LayoutSnapshot(
            double worldWidth,
            double worldHeight,
            List<ZoneLayout> zoneLayouts,
            List<SeatCell> seatCells,
            Map<Integer, SeatCell> seatCellById,
            List<List<SeatCell>> gridRows
    ) {
        LayoutSnapshot {
            zoneLayouts = List.copyOf(zoneLayouts);
            seatCells = List.copyOf(seatCells);
            seatCellById = Collections.unmodifiableMap(new LinkedHashMap<>(seatCellById));
            List<List<SeatCell>> copiedRows = new ArrayList<>(gridRows.size());
            for (List<SeatCell> row : gridRows) {
                copiedRows.add(List.copyOf(row));
            }
            gridRows = List.copyOf(copiedRows);
        }
    }
}
