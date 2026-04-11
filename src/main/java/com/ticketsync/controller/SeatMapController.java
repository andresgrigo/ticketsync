package com.ticketsync.controller;

import com.ticketsync.model.Seat;
import com.ticketsync.model.Zone;
import com.ticketsync.viewmodel.SeatMapViewModel;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class SeatMapController {

    private static final double FOCUS_RING_INSET = 2.0;
    private static final double PAN_THRESHOLD = 4.0;
    private static final Color AVAILABLE_COLOR = Color.web("#4CAF50");
    private static final Color SELECTED_COLOR = Color.web("#FDD835");
    private static final Color SOLD_COLOR = Color.web("#F44336");
    private static final Color DISABLED_COLOR = Color.web("#9E9E9E");
    private static final Color FOCUS_RING_COLOR = Color.web("#1565C0");
    private static final Color SEAT_BORDER_COLOR = Color.web("#37474F");

    @FXML private ScrollPane seatMapScrollPane;
    @FXML private Canvas seatMapCanvas;

    private final Tooltip seatTooltip = new Tooltip();
    private final BooleanProperty interactionEnabled = new SimpleBooleanProperty(true);
    private final Text textMeasurer = new Text();
    private final ListChangeListener<Seat> seatListListener = change -> requestRender();
    private final SetChangeListener<Integer> selectionListener = change -> requestRender();
    private final ChangeListener<Object> focusListener = (obs, oldValue, newValue) -> requestRender();
    private final ChangeListener<Boolean> loadingListener = (obs, oldValue, newValue) -> requestRender();

    private SeatMapViewModel viewModel = new SeatMapViewModel();
    private Supplier<Boolean> isViewActive = () -> true;
    private SeatMapLayoutHelper.LayoutSnapshot currentLayout =
            SeatMapLayoutHelper.buildLayout(List.of(), Map.of());
    private SeatMapLayoutHelper.ViewportTransform viewport =
            new SeatMapLayoutHelper.ViewportTransform(1.0, 0, 0);

    private boolean initialized;
    private boolean renderScheduled;
    private double dragStartX;
    private double dragStartY;
    private double dragStartPanX;
    private double dragStartPanY;
    private boolean backgroundPanCandidate;
    private boolean panning;
    private Integer pressedSeatId;

    @FXML
    public void initialize() {
        initialized = true;
        seatTooltip.setAutoHide(true);
        Tooltip.install(seatMapCanvas, seatTooltip);
        seatMapCanvas.setFocusTraversable(true);

        seatMapCanvas.setOnMouseMoved(this::handleMouseMoved);
        seatMapCanvas.setOnMouseExited(event -> clearTooltip());
        seatMapCanvas.setOnMousePressed(this::handleMousePressed);
        seatMapCanvas.setOnMouseDragged(this::handleMouseDragged);
        seatMapCanvas.setOnMouseReleased(this::handleMouseReleased);
        seatMapCanvas.setOnScroll(this::handleScroll);
        seatMapCanvas.setOnKeyPressed(this::handleKeyPressed);

        seatMapScrollPane.viewportBoundsProperty().addListener((obs, oldValue, newValue) -> requestRender());

        attachViewModelListeners(viewModel);
        requestRender();
    }

    public void setViewModel(SeatMapViewModel seatMapViewModel) {
        SeatMapViewModel newViewModel = Objects.requireNonNull(seatMapViewModel, "seatMapViewModel must not be null");
        if (initialized) {
            detachViewModelListeners(this.viewModel);
        }
        this.viewModel = newViewModel;
        if (initialized) {
            attachViewModelListeners(newViewModel);
            resetViewport();
            requestRender();
        }
    }

    public SeatMapViewModel getViewModel() {
        return viewModel;
    }

    public void setInteractionEnabled(boolean enabled) {
        interactionEnabled.unbind();
        interactionEnabled.set(enabled);
    }

    public void bindInteractionEnabled(ObservableBooleanValue enabledValue) {
        interactionEnabled.unbind();
        interactionEnabled.bind(enabledValue);
    }

    public void setViewActiveCheck(Supplier<Boolean> isActive) {
        isViewActive = Objects.requireNonNull(isActive, "isActive must not be null");
    }

    public void onViewActivated() {
        requestRender();
    }

    private void attachViewModelListeners(SeatMapViewModel model) {
        model.seatsProperty().addListener(seatListListener);
        model.selectedSeatIdsProperty().addListener(selectionListener);
        model.focusedSeatIdProperty().addListener(focusListener);
        model.loadingProperty().addListener(loadingListener);
    }

    private void detachViewModelListeners(SeatMapViewModel model) {
        model.seatsProperty().removeListener(seatListListener);
        model.selectedSeatIdsProperty().removeListener(selectionListener);
        model.focusedSeatIdProperty().removeListener(focusListener);
        model.loadingProperty().removeListener(loadingListener);
    }

    private void requestRender() {
        if (!initialized || renderScheduled) {
            return;
        }
        renderScheduled = true;
        Platform.runLater(() -> {
            renderScheduled = false;
            render();
        });
    }

    private void render() {
        if (!isViewActive.get()) {
            return;
        }

        double viewportWidth = seatMapScrollPane.getViewportBounds().getWidth() > 0
                ? seatMapScrollPane.getViewportBounds().getWidth()
                : seatMapCanvas.getWidth();
        double viewportHeight = seatMapScrollPane.getViewportBounds().getHeight() > 0
                ? seatMapScrollPane.getViewportBounds().getHeight()
                : seatMapCanvas.getHeight();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        if (viewModel.seatsProperty().isEmpty()) {
            renderPlaceholder(viewportWidth, viewportHeight);
            currentLayout = SeatMapLayoutHelper.buildLayout(List.of(), Map.of());
            return;
        }

        Map<Integer, Zone> zonesById = new LinkedHashMap<>();
        for (Zone zone : viewModel.zonesProperty()) {
            zonesById.put(zone.getZoneId(), zone);
        }
        currentLayout = SeatMapLayoutHelper.buildLayout(viewModel.seatsProperty(), zonesById);

        SeatMapLayoutHelper.CanvasExtent extent = SeatMapLayoutHelper.requiredCanvasExtent(
                currentLayout,
                viewportWidth,
                viewportHeight,
                viewport
        );

        boolean resized = false;
        if (seatMapCanvas.getWidth() < extent.width()) {
            seatMapCanvas.setWidth(extent.width());
            resized = true;
        }
        if (seatMapCanvas.getHeight() < extent.height()) {
            seatMapCanvas.setHeight(extent.height());
            resized = true;
        }
        if (resized) {
            seatMapCanvas.setVisible(false);
            Platform.runLater(() -> {
                seatMapCanvas.setVisible(true);
                requestRender();
            });
            return;
        }

        GraphicsContext gc = seatMapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, seatMapCanvas.getWidth(), seatMapCanvas.getHeight());
        gc.save();
        gc.translate(viewport.panX(), viewport.panY());
        gc.scale(viewport.zoom(), viewport.zoom());

        for (SeatMapLayoutHelper.ZoneLayout zoneLayout : currentLayout.zoneLayouts()) {
            gc.setFill(Color.web("#212121"));
            gc.setFont(Font.font(null, FontWeight.BOLD, 16));
            gc.fillText(zoneLayout.zoneLabel(), SeatMapLayoutHelper.PADDING, zoneLayout.headerY() + 16);

            for (SeatMapLayoutHelper.RowLayout rowLayout : zoneLayout.rows()) {
                gc.setFill(Color.web("#616161"));
                gc.setFont(Font.font(11));
                gc.fillText(
                        rowLayout.rowLabel() != null ? rowLayout.rowLabel() : "",
                        SeatMapLayoutHelper.PADDING,
                        rowLayout.topY() + SeatMapLayoutHelper.SEAT_SIZE / 2.0 + 4
                );

                for (SeatMapLayoutHelper.SeatCell cell : rowLayout.seatCells()) {
                    drawSeat(gc, cell);
                }
            }
        }

        gc.restore();
    }

    private void drawSeat(GraphicsContext gc, SeatMapLayoutHelper.SeatCell cell) {
        boolean selected = viewModel.isSeatSelected(cell.seat().getSeatId());
        boolean focused = Objects.equals(viewModel.focusedSeatIdProperty().get(), cell.seat().getSeatId());

        gc.setFill(colorFor(SeatMapLayoutHelper.resolveVisualState(cell.seat(), selected)));
        gc.fillRoundRect(
                cell.worldX(),
                cell.worldY(),
                SeatMapLayoutHelper.SEAT_SIZE,
                SeatMapLayoutHelper.SEAT_SIZE,
                SeatMapLayoutHelper.SEAT_ARC,
                SeatMapLayoutHelper.SEAT_ARC
        );

        gc.setStroke(SEAT_BORDER_COLOR);
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(
                cell.worldX(),
                cell.worldY(),
                SeatMapLayoutHelper.SEAT_SIZE,
                SeatMapLayoutHelper.SEAT_SIZE,
                SeatMapLayoutHelper.SEAT_ARC,
                SeatMapLayoutHelper.SEAT_ARC
        );

        if (focused) {
            gc.setStroke(FOCUS_RING_COLOR);
            gc.setLineWidth(2.0);
            gc.strokeRoundRect(
                    cell.worldX() + FOCUS_RING_INSET,
                    cell.worldY() + FOCUS_RING_INSET,
                    SeatMapLayoutHelper.SEAT_SIZE - 2 * FOCUS_RING_INSET,
                    SeatMapLayoutHelper.SEAT_SIZE - 2 * FOCUS_RING_INSET,
                    SeatMapLayoutHelper.SEAT_ARC,
                    SeatMapLayoutHelper.SEAT_ARC
            );
        }

        String label = cell.seat().getSeatNumber() != null ? cell.seat().getSeatNumber() : "";
        textMeasurer.setFont(Font.font(11));
        textMeasurer.setText(label);
        double textWidth = textMeasurer.getBoundsInLocal().getWidth();
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(11));
        gc.fillText(
                label,
                cell.worldX() + (SeatMapLayoutHelper.SEAT_SIZE - textWidth) / 2.0,
                cell.worldY() + SeatMapLayoutHelper.SEAT_SIZE / 2.0 + 4
        );
    }

    private void renderPlaceholder(double viewportWidth, double viewportHeight) {
        if (seatMapCanvas.getWidth() < viewportWidth) {
            seatMapCanvas.setWidth(viewportWidth);
        }
        if (seatMapCanvas.getHeight() < viewportHeight) {
            seatMapCanvas.setHeight(viewportHeight);
        }

        GraphicsContext gc = seatMapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, seatMapCanvas.getWidth(), seatMapCanvas.getHeight());
        gc.setFill(Color.GRAY);
        gc.setFont(Font.font(13));
        String message = viewModel.loadingProperty().get()
                ? "Loading seat map..."
                : "Seat map will appear when an event is loaded.";
        gc.fillText(message, SeatMapLayoutHelper.PADDING, 30);
    }

    private void handleMouseMoved(MouseEvent event) {
        Optional<SeatMapLayoutHelper.SeatCell> hoveredCell = findSeatAtCanvasCoordinates(event.getX(), event.getY());
        if (hoveredCell.isPresent()) {
            SeatMapLayoutHelper.SeatCell cell = hoveredCell.get();
            seatTooltip.setText(SeatMapLayoutHelper.tooltipText(
                    cell.seat(),
                    cell.zone(),
                    viewModel.isSeatSelected(cell.seat().getSeatId())
            ));
            if (seatMapCanvas.getScene() != null && seatMapCanvas.getScene().getWindow() != null) {
                seatTooltip.show(seatMapCanvas, event.getScreenX() + 12, event.getScreenY() + 12);
            }
        } else {
            clearTooltip();
        }
    }

    private void handleMousePressed(MouseEvent event) {
        seatMapCanvas.requestFocus();
        dragStartX = event.getX();
        dragStartY = event.getY();
        dragStartPanX = viewport.panX();
        dragStartPanY = viewport.panY();
        backgroundPanCandidate = event.getButton() == MouseButton.PRIMARY
                && findSeatAtCanvasCoordinates(event.getX(), event.getY()).isEmpty();
        panning = false;
        pressedSeatId = findSeatAtCanvasCoordinates(event.getX(), event.getY())
                .map(cell -> cell.seat().getSeatId())
                .orElse(null);
        if (pressedSeatId != null) {
            viewModel.setFocusedSeatId(pressedSeatId);
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!backgroundPanCandidate) {
            return;
        }

        double deltaX = event.getX() - dragStartX;
        double deltaY = event.getY() - dragStartY;
        if (!panning && Math.hypot(deltaX, deltaY) < PAN_THRESHOLD) {
            return;
        }

        panning = true;
        viewport = new SeatMapLayoutHelper.ViewportTransform(
                viewport.zoom(),
                dragStartPanX + deltaX,
                dragStartPanY + deltaY
        );
        requestRender();
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (panning) {
            backgroundPanCandidate = false;
            panning = false;
            pressedSeatId = null;
            return;
        }

        if (event.getButton() == MouseButton.PRIMARY && pressedSeatId != null) {
            Integer releasedSeatId = findSeatAtCanvasCoordinates(event.getX(), event.getY())
                    .map(cell -> cell.seat().getSeatId())
                    .orElse(null);
            if (Objects.equals(pressedSeatId, releasedSeatId) && interactionEnabled.get()) {
                viewModel.toggleSeatSelection(pressedSeatId);
            }
        }
        pressedSeatId = null;
    }

    private void handleScroll(ScrollEvent event) {
        if (!event.isControlDown()) {
            return;
        }

        viewport = SeatMapLayoutHelper.zoomAround(viewport, event.getX(), event.getY(), event.getDeltaY());
        requestRender();
        event.consume();
    }

    private void handleKeyPressed(KeyEvent event) {
        if (currentLayout.seatCells().isEmpty()) {
            return;
        }

        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
            Integer focusedSeatId = viewModel.focusedSeatIdProperty().get();
            if (interactionEnabled.get() && focusedSeatId != null) {
                viewModel.toggleSeatSelection(focusedSeatId);
            }
            event.consume();
            return;
        }

        SeatMapLayoutHelper.Direction direction = switch (code) {
            case LEFT -> SeatMapLayoutHelper.Direction.LEFT;
            case RIGHT -> SeatMapLayoutHelper.Direction.RIGHT;
            case UP -> SeatMapLayoutHelper.Direction.UP;
            case DOWN -> SeatMapLayoutHelper.Direction.DOWN;
            default -> null;
        };
        if (direction == null) {
            return;
        }

        Integer nextSeatId = SeatMapLayoutHelper.moveFocus(
                currentLayout,
                viewModel.focusedSeatIdProperty().get(),
                direction
        );
        if (nextSeatId != null) {
            viewModel.setFocusedSeatId(nextSeatId);
        }
        event.consume();
    }

    private Optional<SeatMapLayoutHelper.SeatCell> findSeatAtCanvasCoordinates(double canvasX, double canvasY) {
        double worldX = (canvasX - viewport.panX()) / viewport.zoom();
        double worldY = (canvasY - viewport.panY()) / viewport.zoom();
        return SeatMapLayoutHelper.findSeatAt(currentLayout, worldX, worldY);
    }

    private void clearTooltip() {
        seatTooltip.hide();
        seatTooltip.setText("");
    }

    private void resetViewport() {
        viewport = new SeatMapLayoutHelper.ViewportTransform(1.0, 0, 0);
    }

    private static Color colorFor(SeatMapLayoutHelper.SeatVisualState visualState) {
        return switch (visualState) {
            case AVAILABLE -> AVAILABLE_COLOR;
            case SELECTED -> SELECTED_COLOR;
            case SOLD -> SOLD_COLOR;
            case DISABLED -> DISABLED_COLOR;
        };
    }
}
