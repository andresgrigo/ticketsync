package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import com.ticketsync.model.Zone;
import com.ticketsync.service.EventService;
import com.ticketsync.service.SeatService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.service.ZoneService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LayoutViewTabController {

    private static final Logger LOGGER = LogManager.getLogger(LayoutViewTabController.class);

    private static final int LAYOUT_CELL_SIZE       = 48;
    private static final int LAYOUT_CELL_GAP        = 6;
    private static final int LAYOUT_ROW_GAP         = 8;
    private static final int LAYOUT_ZONE_GAP        = 30;
    private static final int LAYOUT_PADDING         = 16;
    private static final int LAYOUT_ROW_LABEL_WIDTH = 64;
    private static final int LAYOUT_MAX_SEATS_ROW   = 18;

    private final EventService eventService = new EventService();
    private final SeatService  seatService  = new SeatService();
    private final ZoneService  zoneService  = new ZoneService();

    private User currentAdminUser;
    private Supplier<Boolean> isTabActive = () -> true;

    private double layoutZoom = 1.0;
    private double layoutPanX = 0;
    private double layoutPanY = 0;
    private double layoutDragStartX;
    private double layoutDragStartY;
    private double layoutDragStartPanX;
    private double layoutDragStartPanY;

    private List<LayoutSeatCell> layoutSeatCells = new ArrayList<>();
    private List<Seat> layoutCurrentSeats = new ArrayList<>();
    private Map<Integer, Zone> layoutCurrentZoneMap = new HashMap<>();

    private record LayoutSeatCell(Seat seat, Zone zone, double worldX, double worldY) {}

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private ComboBox<Event> layoutEventSelector;
    @FXML private Button layoutExportButton;
    @FXML private Label layoutEventNameLabel;
    @FXML private Label layoutTotalLabel;
    @FXML private Label layoutAvailableLabel;
    @FXML private Label layoutSoldLabel;
    @FXML private Label layoutDisabledLabel;
    @FXML private Canvas layoutCanvas;
    @FXML private ScrollPane layoutScrollPane;
    @FXML private Label layoutHoverLabel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        layoutEventSelector.setConverter(new javafx.util.StringConverter<Event>() {
            @Override public String toString(Event e) {
                return (e == null) ? "" : e.getName();
            }
            @Override public Event fromString(String s) { return null; }
        });
        layoutEventSelector.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Event item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select event\u2026" : item.getName());
            }
        });
        layoutEventSelector.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldEvent, newEvent) -> {
                    if (newEvent != null) {
                        layoutExportButton.setDisable(false);
                        loadLayoutViewAsync(newEvent);
                    } else {
                        layoutExportButton.setDisable(true);
                        clearLayoutView();
                    }
                });

        layoutCanvas.setOnScroll(this::handleLayoutScroll);
        layoutCanvas.setOnMousePressed(this::handleLayoutMousePressed);
        layoutCanvas.setOnMouseDragged(this::handleLayoutMouseDragged);
        layoutCanvas.setOnMouseMoved(this::handleLayoutMouseMoved);
    }

    /** Called by the shell controller once the admin identity is known. */
    public void setAdminUser(User admin) {
        this.currentAdminUser = admin;
        loadLayoutEventsAsync();
    }

    /**
     * Provides a lambda the controller uses to guard canvas renders to the
     * layout tab's active state, preventing Prism RTTexture NPEs.
     */
    public void setTabActiveCheck(Supplier<Boolean> isActive) {
        this.isTabActive = isActive;
    }

    /** Called by the shell when the layout view tab is selected. */
    public void onTabActivated() {
        Event selectedEvent = layoutEventSelector.getSelectionModel().getSelectedItem();
        if (selectedEvent != null) {
            Platform.runLater(() -> loadLayoutViewAsync(selectedEvent));
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadLayoutEventsAsync() {
        User capturedAdmin = currentAdminUser;
        Task<List<Event>> task = new Task<>() {
            @Override
            protected List<Event> call() throws Exception {
                SessionContext.setCurrentUser(capturedAdmin);
                try {
                    return eventService.findAllEvents();
                } finally {
                    SessionContext.clearCurrentUser();
                }
            }
        };
        task.setOnSucceeded(e -> {
            Event previousSelection = layoutEventSelector.getSelectionModel().getSelectedItem();
            layoutEventSelector.getItems().setAll(task.getValue());
            if (previousSelection != null) {
                task.getValue().stream()
                        .filter(ev -> ev.getEventId() == previousSelection.getEventId())
                        .findFirst()
                        .ifPresent(ev -> layoutEventSelector.getSelectionModel().select(ev));
            }
        });
        task.setOnFailed(e ->
                LOGGER.error("Failed to load events for layout selector", task.getException()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void loadLayoutViewAsync(Event event) {
        if (currentAdminUser == null) return;
        User capturedAdmin = currentAdminUser;
        Task<Pair<List<Seat>, List<Zone>>> task = new Task<>() {
            @Override
            protected Pair<List<Seat>, List<Zone>> call() throws Exception {
                SessionContext.setCurrentUser(capturedAdmin);
                try {
                    List<Seat> seats = seatService.getSeatsForEvent(event.getEventId());
                    List<Zone> zones = zoneService.getZonesByEvent(event.getEventId());
                    return new Pair<>(seats, zones);
                } finally {
                    SessionContext.clearCurrentUser();
                }
            }
        };
        task.setOnSucceeded(e -> {
            Event currentSelection = layoutEventSelector.getSelectionModel().getSelectedItem();
            if (currentSelection == null
                    || currentSelection.getEventId() != event.getEventId()) return;

            List<Seat> seats = task.getValue().getKey();
            List<Zone> zones = task.getValue().getValue();
            Map<Integer, Zone> zoneMap = zones.stream()
                    .collect(Collectors.toMap(Zone::getZoneId, z -> z));

            long available = seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
            long sold      = seats.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();
            long disabled  = seats.stream().filter(s -> s.getStatus() == SeatStatus.DISABLED).count();
            layoutEventNameLabel.setText(event.getName());
            layoutTotalLabel.setText("Total: " + seats.size());
            layoutAvailableLabel.setText("Available: " + available);
            layoutSoldLabel.setText("Sold: " + sold);
            layoutDisabledLabel.setText("Disabled: " + disabled);

            layoutZoom = 1.0;
            layoutPanX = 0;
            layoutPanY = 0;
            layoutCurrentSeats = seats;
            layoutCurrentZoneMap = zoneMap;
            renderLayoutView(seats, zoneMap);
        });
        task.setOnFailed(e -> {
            LOGGER.error("Failed to load layout view for event {}", event.getEventId(),
                    task.getException());
            layoutHoverLabel.setText("Error loading layout. Please try again.");
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Canvas rendering ──────────────────────────────────────────────────────

    private void clearLayoutView() {
        layoutEventNameLabel.setText("No event selected");
        layoutTotalLabel.setText("Total: \u2014");
        layoutAvailableLabel.setText("Available: \u2014");
        layoutSoldLabel.setText("Sold: \u2014");
        layoutDisabledLabel.setText("Disabled: \u2014");
        layoutHoverLabel.setText("");
        layoutSeatCells.clear();
        layoutCurrentSeats.clear();
        layoutCurrentZoneMap.clear();
        layoutZoom = 1.0;
        layoutPanX = 0;
        layoutPanY = 0;
        double w = layoutCanvas.getWidth();
        double h = layoutCanvas.getHeight();
        if (w > 0 && h > 0) {
            GraphicsContext gc = layoutCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, w, h);
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font(13));
            gc.fillText("Select an event above", LAYOUT_PADDING, 30);
        }
    }

    private void reRenderLayoutViewFromCache() {
        if (!layoutCurrentSeats.isEmpty()) {
            renderLayoutView(layoutCurrentSeats, layoutCurrentZoneMap);
        }
    }

    private void renderLayoutView(List<Seat> allSeats, Map<Integer, Zone> zoneMap) {
        if (!isTabActive.get()) return;
        double canvasW = layoutCanvas.getWidth();
        double canvasH = layoutCanvas.getHeight();
        if (canvasW <= 0 || canvasH <= 0) return;

        layoutSeatCells.clear();

        if (allSeats.isEmpty()) {
            GraphicsContext gc = layoutCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvasW, canvasH);
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font(13));
            gc.fillText("No seats configured for this event", LAYOUT_PADDING, 30);
            return;
        }

        LinkedHashMap<Integer, List<Seat>> byZone = new LinkedHashMap<>();
        for (Seat s : allSeats) {
            byZone.computeIfAbsent(s.getZoneId(), k -> new ArrayList<>()).add(s);
        }

        double worldHeight = LAYOUT_PADDING;
        for (Map.Entry<Integer, List<Seat>> entry : byZone.entrySet()) {
            worldHeight += 24 + 8;
            java.util.TreeMap<String, List<Seat>> byRow =
                    new java.util.TreeMap<>(LayoutViewTabController::numericStringCompare);
            for (Seat s : entry.getValue()) {
                byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
            }
            worldHeight += byRow.size() * (LAYOUT_CELL_SIZE + LAYOUT_ROW_GAP);
            worldHeight += LAYOUT_ZONE_GAP;
        }
        worldHeight += LAYOUT_PADDING;

        int maxSeatsPerRow = (int) allSeats.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getZoneId() + ":" + s.getRowNumber(),
                        Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).max().orElse(0L);
        double worldWidth = LAYOUT_PADDING + LAYOUT_ROW_LABEL_WIDTH
                + maxSeatsPerRow * (LAYOUT_CELL_SIZE + LAYOUT_CELL_GAP) + LAYOUT_PADDING;

        final double MAX_TEX = 8192.0;
        double scaledW = Math.min(
                Math.max(canvasW, worldWidth  * layoutZoom + Math.abs(layoutPanX)), MAX_TEX);
        double scaledH = Math.min(
                Math.max(canvasH, worldHeight * layoutZoom + Math.abs(layoutPanY)), MAX_TEX);
        boolean layoutResized = false;
        if (layoutCanvas.getWidth() < scaledW)  { layoutCanvas.setWidth(scaledW);  layoutResized = true; }
        if (layoutCanvas.getHeight() < scaledH) { layoutCanvas.setHeight(scaledH); layoutResized = true; }
        if (layoutResized) {
            layoutCanvas.setVisible(false);
            Platform.runLater(() -> {
                layoutCanvas.setVisible(true);
                Platform.runLater(() -> renderLayoutView(allSeats, zoneMap));
            });
            return;
        }

        GraphicsContext gc = layoutCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, layoutCanvas.getWidth(), layoutCanvas.getHeight());
        gc.save();
        gc.translate(layoutPanX, layoutPanY);
        gc.scale(layoutZoom, layoutZoom);

        Text measurer = new Text();
        double yOffset = LAYOUT_PADDING;

        for (Map.Entry<Integer, List<Seat>> zoneEntry : byZone.entrySet()) {
            Zone zone = zoneMap.get(zoneEntry.getKey());
            String zoneName = (zone != null) ? zone.getName() : "Zone " + zoneEntry.getKey();

            gc.setFill(Color.web("#212121"));
            gc.setFont(Font.font(null, FontWeight.BOLD, 16));
            gc.fillText(zoneName, LAYOUT_PADDING, yOffset + 16);
            yOffset += 24 + 8;

            java.util.TreeMap<String, List<Seat>> byRow =
                    new java.util.TreeMap<>(LayoutViewTabController::numericStringCompare);
            for (Seat s : zoneEntry.getValue()) {
                byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
            }

            for (Map.Entry<String, List<Seat>> rowEntry : byRow.entrySet()) {
                double xOffset = LAYOUT_PADDING;
                gc.setFill(Color.web("#616161"));
                gc.setFont(Font.font(11));
                gc.fillText(rowEntry.getKey(), xOffset, yOffset + LAYOUT_CELL_SIZE / 2.0 + 4);
                xOffset += LAYOUT_ROW_LABEL_WIDTH;

                List<Seat> rowSeats = new ArrayList<>(rowEntry.getValue());
                rowSeats.sort((a, b) ->
                        numericStringCompare(a.getSeatNumber(), b.getSeatNumber()));
                for (Seat seat : rowSeats) {
                    Color fill = switch (seat.getStatus()) {
                        case AVAILABLE -> Color.web("#4CAF50");
                        case SOLD      -> Color.web("#F44336");
                        case DISABLED  -> Color.web("#9E9E9E");
                        default        -> Color.web("#9E9E9E");
                    };
                    gc.setFill(fill);
                    gc.fillRoundRect(xOffset, yOffset, LAYOUT_CELL_SIZE, LAYOUT_CELL_SIZE, 6, 6);
                    gc.setFill(Color.WHITE);
                    gc.setFont(Font.font(11));
                    String label = seat.getSeatNumber() != null ? seat.getSeatNumber() : "";
                    measurer.setText(label);
                    measurer.setFont(Font.font(11));
                    double textW = measurer.getBoundsInLocal().getWidth();
                    gc.fillText(label,
                            xOffset + (LAYOUT_CELL_SIZE - textW) / 2.0,
                            yOffset + LAYOUT_CELL_SIZE / 2.0 + 4);
                    layoutSeatCells.add(new LayoutSeatCell(seat, zone, xOffset, yOffset));
                    xOffset += LAYOUT_CELL_SIZE + LAYOUT_CELL_GAP;
                }
                yOffset += LAYOUT_CELL_SIZE + LAYOUT_ROW_GAP;
            }
            yOffset += LAYOUT_ZONE_GAP;
        }
        gc.restore();
    }

    // ── Canvas event handlers ─────────────────────────────────────────────────

    private void handleLayoutScroll(ScrollEvent e) {
        if (e.getDeltaY() == 0) return;
        double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
        double newZoom = Math.max(0.5, Math.min(3.0, layoutZoom * delta));
        if (newZoom == layoutZoom) return;
        double mouseX = e.getX();
        double mouseY = e.getY();
        double worldX = (mouseX - layoutPanX) / layoutZoom;
        double worldY = (mouseY - layoutPanY) / layoutZoom;
        layoutZoom = newZoom;
        layoutPanX = mouseX - worldX * layoutZoom;
        layoutPanY = mouseY - worldY * layoutZoom;
        reRenderLayoutViewFromCache();
        e.consume();
    }

    private void handleLayoutMousePressed(MouseEvent e) {
        layoutDragStartX    = e.getX();
        layoutDragStartY    = e.getY();
        layoutDragStartPanX = layoutPanX;
        layoutDragStartPanY = layoutPanY;
    }

    private void handleLayoutMouseDragged(MouseEvent e) {
        layoutPanX = layoutDragStartPanX + (e.getX() - layoutDragStartX);
        layoutPanY = layoutDragStartPanY + (e.getY() - layoutDragStartY);
        reRenderLayoutViewFromCache();
    }

    private void handleLayoutMouseMoved(MouseEvent e) {
        double worldX = (e.getX() - layoutPanX) / layoutZoom;
        double worldY = (e.getY() - layoutPanY) / layoutZoom;
        for (LayoutSeatCell cell : layoutSeatCells) {
            if (worldX >= cell.worldX() && worldX <= cell.worldX() + LAYOUT_CELL_SIZE
                    && worldY >= cell.worldY() && worldY <= cell.worldY() + LAYOUT_CELL_SIZE) {
                String zoneName = (cell.zone() != null) ? cell.zone().getName() : "?";
                String price = (cell.zone() != null && cell.zone().getPrice() != null)
                        ? cell.zone().getPrice().toPlainString() : "?";
                String rowNum  = cell.seat().getRowNumber()  != null ? cell.seat().getRowNumber()  : "?";
                String seatNum = cell.seat().getSeatNumber() != null ? cell.seat().getSeatNumber() : "?";
                layoutHoverLabel.setText("Zone: " + zoneName
                        + ", Row: " + rowNum + ", Seat: " + seatNum
                        + ", Price: \u20AC" + price
                        + ", Status: " + cell.seat().getStatus());
                return;
            }
        }
        layoutHoverLabel.setText("");
    }

    // ── Export handler ────────────────────────────────────────────────────────

    @FXML
    private void handleExportLayout() {
        Event selectedEvent = layoutEventSelector.getSelectionModel().getSelectedItem();
        if (selectedEvent == null || layoutCurrentSeats.isEmpty()) return;

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Save Seating Layout PDF");
        String safeName = selectedEvent.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        fileChooser.setInitialFileName(safeName + "-layout.pdf");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialDirectory(new java.io.File(System.getProperty("user.home")));

        java.io.File file = fileChooser.showSaveDialog(layoutCanvas.getScene().getWindow());
        if (file == null) return;

        List<Seat> seatSnapshot = new ArrayList<>(layoutCurrentSeats);
        Map<Integer, Zone> zoneSnapshot = new HashMap<>(layoutCurrentZoneMap);
        layoutExportButton.setDisable(true);
        layoutHoverLabel.setText("Exporting PDF\u2026");

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                exportLayoutToPdf(file, selectedEvent, seatSnapshot, zoneSnapshot);
                return null;
            }
        };
        exportTask.setOnSucceeded(e -> {
            layoutExportButton.setDisable(false);
            layoutHoverLabel.setText("Layout exported: " + file.getAbsolutePath());
        });
        exportTask.setOnFailed(e -> {
            layoutExportButton.setDisable(false);
            LOGGER.error("Failed to export layout PDF", exportTask.getException());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export Failed");
            alert.setHeaderText(null);
            alert.setContentText(
                    "Could not generate the PDF. Please check that the destination folder"
                    + " is writable and try again.");
            alert.showAndWait();
        });
        Thread t = new Thread(exportTask);
        t.setDaemon(true);
        t.start();
    }

    private void exportLayoutToPdf(java.io.File file, Event event,
            List<Seat> seats, Map<Integer, Zone> zoneMap) throws Exception {
        float pageW    = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth();
        float pageH    = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight();
        float margin   = 40f;
        float labelW   = 50f;
        float cellSize = 22f;
        float cellGap  = 3f;
        float rowGap   = 6f;
        float zoneGap  = 14f;
        float lineH    = cellSize + rowGap;
        int seatsPerLine = Math.min(LAYOUT_MAX_SEATS_ROW,
                Math.max(1, (int) ((pageW - 2 * margin - labelW) / (cellSize + cellGap))));

        org.apache.pdfbox.pdmodel.font.PDType1Font pdfBold = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);
        org.apache.pdfbox.pdmodel.font.PDType1Font pdfNormal = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);

        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage curPage =
                    new org.apache.pdfbox.pdmodel.PDPage(
                            org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(curPage);
            org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, curPage);
            float yPos = pageH - margin;

            try {
                cs.beginText();
                cs.setFont(pdfBold, 14);
                cs.newLineAtOffset(margin, yPos);
                cs.showText(toPdfSafe("Seating Layout: " + event.getName()));
                cs.endText();
                yPos -= 28;

                LinkedHashMap<Integer, List<Seat>> byZone = new LinkedHashMap<>();
                for (Seat s : seats) {
                    byZone.computeIfAbsent(s.getZoneId(), k -> new ArrayList<>()).add(s);
                }

                for (Map.Entry<Integer, List<Seat>> zoneEntry : byZone.entrySet()) {
                    Zone zone = zoneMap.get(zoneEntry.getKey());
                    String zoneName = (zone != null)
                            ? toPdfSafe(zone.getName()) : "Zone " + zoneEntry.getKey();

                    if (yPos - (28 + lineH) < margin) {
                        cs.close();
                        curPage = new org.apache.pdfbox.pdmodel.PDPage(
                                org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                        doc.addPage(curPage);
                        cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, curPage);
                        yPos = pageH - margin;
                    }

                    cs.beginText();
                    cs.setFont(pdfBold, 12);
                    cs.setNonStrokingColor(0.13f, 0.13f, 0.13f);
                    cs.newLineAtOffset(margin, yPos);
                    cs.showText(zoneName);
                    cs.endText();
                    yPos -= 28;

                    java.util.TreeMap<String, List<Seat>> byRow =
                            new java.util.TreeMap<>(LayoutViewTabController::numericStringCompare);
                    for (Seat s : zoneEntry.getValue()) {
                        byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
                    }

                    for (Map.Entry<String, List<Seat>> rowEntry : byRow.entrySet()) {
                        String rowLabel = rowEntry.getKey() != null
                                ? toPdfSafe(rowEntry.getKey()) : "";
                        List<Seat> rowSeats = new ArrayList<>(rowEntry.getValue());
                        rowSeats.sort((a, b) ->
                                numericStringCompare(a.getSeatNumber(), b.getSeatNumber()));

                        for (int chunk = 0; chunk < rowSeats.size(); chunk += seatsPerLine) {
                            List<Seat> line = rowSeats.subList(
                                    chunk, Math.min(chunk + seatsPerLine, rowSeats.size()));

                            if (yPos - lineH < margin) {
                                cs.close();
                                curPage = new org.apache.pdfbox.pdmodel.PDPage(
                                        org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                                doc.addPage(curPage);
                                cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, curPage);
                                yPos = pageH - margin;
                            }

                            if (chunk == 0) {
                                cs.beginText();
                                cs.setFont(pdfNormal, 9);
                                cs.setNonStrokingColor(0.38f, 0.38f, 0.38f);
                                cs.newLineAtOffset(margin, yPos + cellSize / 2f - 4);
                                cs.showText(rowLabel);
                                cs.endText();
                            }

                            float xPos = margin + labelW;
                            for (Seat seat : line) {
                                float[] rgb = switch (seat.getStatus()) {
                                    case AVAILABLE -> new float[]{0.298f, 0.686f, 0.314f};
                                    case SOLD      -> new float[]{0.957f, 0.263f, 0.212f};
                                    case DISABLED  -> new float[]{0.620f, 0.620f, 0.620f};
                                    default        -> new float[]{0.620f, 0.620f, 0.620f};
                                };
                                cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
                                cs.addRect(xPos, yPos, cellSize, cellSize);
                                cs.fill();

                                String seatNum = seat.getSeatNumber() != null
                                        ? toPdfSafe(seat.getSeatNumber()) : "";
                                if (!seatNum.isEmpty()) {
                                    float textWidth =
                                            pdfNormal.getStringWidth(seatNum) / 1000f * 8;
                                    cs.beginText();
                                    cs.setFont(pdfNormal, 8);
                                    cs.setNonStrokingColor(1f, 1f, 1f);
                                    cs.newLineAtOffset(
                                            xPos + (cellSize - textWidth) / 2f,
                                            yPos + cellSize / 2f - 3);
                                    cs.showText(seatNum);
                                    cs.endText();
                                }
                                xPos += cellSize + cellGap;
                            }
                            yPos -= lineH;
                        }
                    }
                    yPos -= zoneGap;
                }
            } finally {
                if (cs != null) cs.close();
            }
            doc.save(file);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String toPdfSafe(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c <= 0xFF ? c : '?');
        }
        return sb.toString();
    }

    private static int numericStringCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }
}
