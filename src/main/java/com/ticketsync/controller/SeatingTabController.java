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
import com.ticketsync.util.DialogThemeHelper;
import com.ticketsync.util.ThemePalette;
import com.ticketsync.util.ThemeStyleHelper;
import com.ticketsync.viewmodel.SeatManagementViewModel;
import com.ticketsync.viewmodel.ZoneManagementViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SeatingTabController {

    private static final Logger LOGGER = LogManager.getLogger(SeatingTabController.class);

    private static final int CELL_SIZE = 32;
    private static final int CELL_GAP  = 4;
    private static final int ROW_GAP   = 8;
    private static final int PADDING   = 10;

    private final ZoneService zoneService = new ZoneService();
    private final SeatService seatService = new SeatService();
    private final EventService eventService = new EventService();

    private ZoneManagementViewModel zonesViewModel;
    private SeatManagementViewModel seatsViewModel;
    private User currentAdminUser;
    private Map<Integer, Integer> zoneSeatCounts = new HashMap<>();
    private String pendingToggleNote;
    private boolean toggleInProgress;
    private List<SeatCell> seatCells = new ArrayList<>();
    private Supplier<Boolean> isTabActive = () -> true;

    private record SeatCell(Seat seat, double x, double y) {}

    // ── FXML fields: zones ────────────────────────────────────────────────────

    @FXML private ComboBox<Event> zonesEventSelector;
    @FXML private Button zonesAddButton;
    @FXML private Button zonesEditButton;
    @FXML private Button zonesDeleteButton;
    @FXML private Label zonesStatusLabel;
    @FXML private TableView<Zone> zonesTable;
    @FXML private TableColumn<Zone, String> zonesNameColumn;
    @FXML private TableColumn<Zone, String> zonesPriceColumn;
    @FXML private TableColumn<Zone, String> zonesSeatCountColumn;

    // ── FXML fields: seats ────────────────────────────────────────────────────

    @FXML private ComboBox<Zone> seatsZoneSelector;
    @FXML private TextField seatsRowField;
    @FXML private TextField seatsFromField;
    @FXML private TextField seatsToField;
    @FXML private Button seatsGenerateButton;
    @FXML private Button seatsDeleteButton;
    @FXML private Button seatsMarkUnavailableButton;
    @FXML private Button seatsMarkAvailableButton;
    @FXML private Label seatsStatusLabel;
    @FXML private TableView<Seat> seatsTable;
    @FXML private TableColumn<Seat, String> seatsRowColumn;
    @FXML private TableColumn<Seat, String> seatsSeatColumn;
    @FXML private TableColumn<Seat, String> seatsStatusColumn;
    @FXML private Canvas seatMapCanvas;
    @FXML private ScrollPane seatMapScrollPane;
    @FXML private Label seatMapHoverLabel;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Zone table setup
        zonesViewModel = new ZoneManagementViewModel();
        zonesTable.setItems(zonesViewModel.zonesProperty());

        zonesNameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName() != null
                        ? data.getValue().getName() : ""));
        zonesPriceColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getPrice() != null
                        ? String.format("%.2f", data.getValue().getPrice()) : "\u2014"));
        zonesSeatCountColumn.setCellValueFactory(data -> {
            int count = zoneSeatCounts.getOrDefault(data.getValue().getZoneId(), 0);
            return new SimpleStringProperty(String.valueOf(count));
        });

        zonesViewModel.selectedZoneProperty().bind(
                zonesTable.getSelectionModel().selectedItemProperty());
        zonesEditButton.disableProperty().bind(
                zonesTable.getSelectionModel().selectedItemProperty().isNull());
        zonesDeleteButton.disableProperty().bind(
                zonesTable.getSelectionModel().selectedItemProperty().isNull());

        // Zone event selector cell factories
        zonesEventSelector.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Event item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        zonesEventSelector.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Event item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select event\u2026" : item.getName());
            }
        });
        zonesEventSelector.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldEvent, newEvent) -> {
                    if (newEvent != null) {
                        zonesAddButton.setDisable(false);
                        loadZonesAsync(newEvent.getEventId());
                    } else {
                        zonesAddButton.setDisable(true);
                        zoneSeatCounts.clear();
                        zonesViewModel.setZones(Collections.emptyList());
                        zonesStatusLabel.setText("");
                    }
                });

        // Seat table setup
        seatsViewModel = new SeatManagementViewModel();
        seatsTable.setItems(seatsViewModel.seatsProperty());
        seatsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        seatsRowColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRowNumber() != null
                        ? data.getValue().getRowNumber() : ""));
        seatsSeatColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSeatNumber() != null
                        ? data.getValue().getSeatNumber() : ""));
        seatsStatusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getStatus() != null
                        ? data.getValue().getStatus().name() : ""));
        seatsStatusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                ThemeStyleHelper.applyManagedStateClass(
                        getStyleClass(),
                        "status-table-cell",
                        ThemeStyleHelper.STATUS_STATE_CLASSES,
                        null
                );
                if (empty || val == null) {
                    setText(null);
                } else {
                    setText(val);
                    ThemeStyleHelper.applyManagedStateClass(
                            getStyleClass(),
                            "status-table-cell",
                            ThemeStyleHelper.STATUS_STATE_CLASSES,
                            ThemeStyleHelper.seatStatusClass(val)
                    );
                }
            }
        });

        seatsZoneSelector.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Zone item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        seatsZoneSelector.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Zone item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select zone\u2026" : item.getName());
            }
        });

        seatsDeleteButton.disableProperty().bind(
                seatsTable.getSelectionModel().selectedItemProperty().isNull());

        seatsMarkUnavailableButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> seatsTable.getSelectionModel().getSelectedItems().stream()
                                .noneMatch(s -> s.getStatus() == SeatStatus.AVAILABLE),
                        seatsTable.getSelectionModel().getSelectedItems()));
        seatsMarkAvailableButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> seatsTable.getSelectionModel().getSelectedItems().stream()
                                .noneMatch(s -> s.getStatus() == SeatStatus.DISABLED),
                        seatsTable.getSelectionModel().getSelectedItems()));

        seatsRowField.textProperty().addListener((obs, o, n) -> updateGenerateButtonState());
        seatsFromField.textProperty().addListener((obs, o, n) -> updateGenerateButtonState());
        seatsToField.textProperty().addListener((obs, o, n) -> updateGenerateButtonState());
        seatsZoneSelector.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> updateGenerateButtonState());

        seatsFromField.setTextFormatter(new TextFormatter<>(
                change -> change.getControlNewText().matches("\\d*") ? change : null));
        seatsToField.setTextFormatter(new TextFormatter<>(
                change -> change.getControlNewText().matches("\\d*") ? change : null));
        seatsRowField.setTextFormatter(new TextFormatter<>(
                change -> change.getControlNewText().length() <= 10 ? change : null));

        seatsZoneSelector.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldZone, newZone) -> {
                    if (newZone != null) {
                        loadSeatsAsync(newZone.getZoneId());
                    } else {
                        seatsViewModel.setSeats(Collections.emptyList());
                        seatsStatusLabel.setText("");
                        Platform.runLater(this::renderSeatMap);
                    }
                });

        // sync zone TableView selection → seatsZoneSelector
        zonesTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newZone) -> {
                    if (newZone != null) {
                        seatsZoneSelector.getItems().stream()
                                .filter(z -> z.getZoneId() == newZone.getZoneId())
                                .findFirst()
                                .ifPresent(z -> seatsZoneSelector.getSelectionModel().select(z));
                    }
                });

        seatsTable.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<Seat>)
                        change -> Platform.runLater(this::renderSeatMap));

        seatMapCanvas.setOnMouseMoved(this::handleCanvasMouseMoved);
        seatMapCanvas.setOnMousePressed(this::handleCanvasMousePressed);
        seatMapCanvas.setOnMouseDragged(this::handleCanvasMouseDragged);
    }

    /** Called by the shell controller once the admin identity is known. */
    public void setAdminUser(User admin) {
        this.currentAdminUser = admin;
        loadZonesEventSelectorAsync();
    }

    /**
     * Provides a lambda the controller uses to guard canvas renders to the
     * seating tab's active state, preventing Prism RTTexture NPEs.
     */
    public void setTabActiveCheck(Supplier<Boolean> isActive) {
        this.isTabActive = isActive;
    }

    /** Called by the shell when the seating tab is selected. */
    public void onTabActivated() {
        Platform.runLater(this::renderSeatMap);
    }

    // ── Zone loading ──────────────────────────────────────────────────────────

    private void loadZonesEventSelectorAsync() {
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
            Event previousSelection = zonesEventSelector.getSelectionModel().getSelectedItem();
            zonesEventSelector.getItems().setAll(task.getValue());
            if (previousSelection != null) {
                task.getValue().stream()
                        .filter(ev -> ev.getEventId() == previousSelection.getEventId())
                        .findFirst()
                        .ifPresent(ev -> zonesEventSelector.getSelectionModel().select(ev));
            }
        });
        task.setOnFailed(e ->
                LOGGER.error("Failed to load events for zone selector", task.getException()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void loadZonesAsync(int eventId) {
        zonesStatusLabel.setText("Loading...");
        Task<Pair<List<Zone>, Map<Integer, Integer>>> task = new Task<>() {
            @Override
            protected Pair<List<Zone>, Map<Integer, Integer>> call() throws Exception {
                List<Zone> zones = zoneService.getZonesByEvent(eventId);
                Map<Integer, Integer> counts = new HashMap<>();
                for (Zone z : zones) {
                    counts.put(z.getZoneId(), zoneService.countSeatsForZone(z.getZoneId()));
                }
                return new Pair<>(zones, counts);
            }
        };
        task.setOnSucceeded(e -> {
            Pair<List<Zone>, Map<Integer, Integer>> result = task.getValue();
            zoneSeatCounts = result.getValue();
            zonesViewModel.setZones(result.getKey());
            Zone previousSeatZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            seatsZoneSelector.getItems().setAll(result.getKey());
            if (previousSeatZone != null) {
                result.getKey().stream()
                        .filter(z -> z.getZoneId() == previousSeatZone.getZoneId())
                        .findFirst()
                        .ifPresent(z -> seatsZoneSelector.getSelectionModel().select(z));
            }
            zonesStatusLabel.setText("");
        });
        task.setOnFailed(e -> {
            LOGGER.error("Failed to load zones", task.getException());
            zonesStatusLabel.setText("Error loading zones");
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Zone CRUD handlers ────────────────────────────────────────────────────

    @FXML
    private void handleAddZone() {
        Event selectedEvent = zonesEventSelector.getSelectionModel().getSelectedItem();
        if (selectedEvent == null) return;

        FXMLLoader loader = createZoneFormLoader();
        if (loader == null) return;

        ZoneFormController formController = loader.getController();
        formController.setMode(ZoneFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = buildZoneDialog("Add Zone", loader.getRoot());
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION,
                event -> { if (!formController.validate()) event.consume(); });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String name = formController.getName();
            BigDecimal price = formController.getPrice();
            int eventId = selectedEvent.getEventId();
            User capturedAdmin = currentAdminUser;
            Task<Integer> task = new Task<>() {
                @Override
                protected Integer call() throws Exception {
                    SessionContext.setCurrentUser(capturedAdmin);
                    try {
                        return zoneService.createZone(eventId, name, price);
                    } finally {
                        SessionContext.clearCurrentUser();
                    }
                }
            };
            task.setOnSucceeded(e -> loadZonesAsync(eventId));
            task.setOnFailed(e -> {
                LOGGER.error("Add zone failed", task.getException());
                showSecurityAwareError("Add Zone Failed", task.getException());
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    @FXML
    private void handleEditZone() {
        Zone selectedZone = zonesViewModel.selectedZoneProperty().get();
        if (selectedZone == null) return;

        FXMLLoader loader = createZoneFormLoader();
        if (loader == null) return;

        ZoneFormController formController = loader.getController();
        formController.setMode(ZoneFormController.Mode.EDIT);
        formController.setZone(selectedZone);

        Dialog<ButtonType> dialog = buildZoneDialog("Edit Zone", loader.getRoot());
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION,
                event -> { if (!formController.validate()) event.consume(); });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Zone updatedZone = formController.getZoneFromForm();
            int eventId = selectedZone.getEventId();
            User capturedAdmin = currentAdminUser;
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    SessionContext.setCurrentUser(capturedAdmin);
                    try {
                        zoneService.updateZone(updatedZone);
                    } finally {
                        SessionContext.clearCurrentUser();
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadZonesAsync(eventId));
            task.setOnFailed(e -> {
                LOGGER.error("Edit zone failed", task.getException());
                showSecurityAwareError("Edit Zone Failed", task.getException());
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    @FXML
    private void handleDeleteZone() {
        Zone selectedZone = zonesViewModel.selectedZoneProperty().get();
        if (selectedZone == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogThemeHelper.apply(confirm);
        confirm.setTitle("Delete Zone");
        confirm.setHeaderText(null);
        String zoneName = selectedZone.getName() != null ? selectedZone.getName() : "(unnamed)";
        confirm.setContentText(
                "Delete zone '" + zoneName + "'? All seats in this zone will be removed.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            int zoneId = selectedZone.getZoneId();
            int eventId = selectedZone.getEventId();
            User capturedAdmin = currentAdminUser;
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    SessionContext.setCurrentUser(capturedAdmin);
                    try {
                        zoneService.deleteZone(zoneId);
                    } finally {
                        SessionContext.clearCurrentUser();
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadZonesAsync(eventId));
            task.setOnFailed(e -> {
                LOGGER.error("Delete zone failed", task.getException());
                showSecurityAwareError("Delete Zone Failed", task.getException());
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    // ── Seat loading ──────────────────────────────────────────────────────────

    private void loadSeatsAsync(int zoneId) {
        Task<List<Seat>> task = new Task<>() {
            @Override
            protected List<Seat> call() throws Exception {
                return seatService.getSeatsByZone(zoneId);
            }
        };
        task.setOnSucceeded(e -> {
            List<Seat> seats = new ArrayList<>(task.getValue());
            seats.sort(Comparator
                    .comparing(Seat::getRowNumber, SeatingTabController::numericStringCompare)
                    .thenComparing(Seat::getSeatNumber, SeatingTabController::numericStringCompare));
            seatsViewModel.setSeats(seats);
            Platform.runLater(this::renderSeatMap);
            long avail    = seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
            long disabled = seats.stream().filter(s -> s.getStatus() == SeatStatus.DISABLED).count();
            long sold     = seats.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();
            String countSummary = avail + " available, " + disabled + " disabled, " + sold + " sold";
            if (pendingToggleNote != null) {
                seatsStatusLabel.setText(countSummary + " \u2014 " + pendingToggleNote);
                pendingToggleNote = null;
            } else {
                seatsStatusLabel.setText(countSummary);
            }
        });
        task.setOnFailed(e ->
                LOGGER.error("Failed to load seats for zone {}", zoneId, task.getException()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Seat CRUD handlers ────────────────────────────────────────────────────

    @FXML
    private void handleGenerateSeats() {
        Zone selectedZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
        if (selectedZone == null) return;

        String row = seatsRowField.getText();
        if (row == null || row.isBlank()) {
            showGenerateError("Error: Row must not be blank");
            return;
        }
        int from;
        int to;
        try {
            from = Integer.parseInt(seatsFromField.getText());
            to   = Integer.parseInt(seatsToField.getText());
        } catch (NumberFormatException e) {
            showGenerateError("Error: From and To must be valid integers");
            return;
        }
        if (from < 1) { showGenerateError("Error: From must be >= 1"); return; }
        if (to < from) { showGenerateError("Error: To must be >= From"); return; }
        if (to - from + 1 > 1000) { showGenerateError("Error: Range must not exceed 1000 seats"); return; }

        int zoneId = selectedZone.getZoneId();
        User capturedAdmin = currentAdminUser;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SessionContext.setCurrentUser(capturedAdmin);
                try {
                    seatService.generateSeats(zoneId, row, from, to);
                } finally {
                    SessionContext.clearCurrentUser();
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            loadSeatsAsync(zoneId);
            Event currentEvent = zonesEventSelector.getSelectionModel().getSelectedItem();
            if (currentEvent != null) loadZonesAsync(currentEvent.getEventId());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            LOGGER.error("Generate seats failed", ex);
            String msg;
            if (isDuplicateError(ex)) {
                msg = "Error: Duplicate seats detected. No seats were created.";
            } else if (ex != null && ex.getMessage() != null
                    && ex.getMessage().contains("value too long")) {
                msg = "Error: The row name or seat number is too long (max 10 characters)."
                        + " Please shorten it and try again.";
            } else {
                msg = "Error generating seats. Please try again.";
            }
            Alert alert = new Alert(Alert.AlertType.ERROR);
            DialogThemeHelper.apply(alert);
            alert.setTitle("Generate Seats Failed");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
            Zone z = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (z != null) loadSeatsAsync(z.getZoneId());
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleMarkUnavailable() {
        if (toggleInProgress) return;

        List<Seat> selected = new ArrayList<>(seatsTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        List<Seat> eligible = selected.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE).toList();
        long soldCount = selected.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();

        if (eligible.isEmpty()) {
            seatsStatusLabel.setText(soldCount > 0
                    ? "SOLD seats skipped \u2014 no changes made"
                    : "No eligible seats in selection.");
            return;
        }

        List<Integer> seatIds = eligible.stream().map(Seat::getSeatId).toList();
        Zone selectedZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
        if (selectedZone == null) { seatsStatusLabel.setText("No zone selected."); return; }
        int zoneId = selectedZone.getZoneId();

        User capturedAdmin = currentAdminUser;
        if (capturedAdmin == null) { seatsStatusLabel.setText("Error: no active admin session."); return; }

        toggleInProgress = true;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SessionContext.setCurrentUser(capturedAdmin);
                try {
                    seatService.updateSeatStatus(seatIds, SeatStatus.DISABLED);
                } finally {
                    SessionContext.clearCurrentUser();
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            toggleInProgress = false;
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            pendingToggleNote = soldCount > 0
                    ? "Updated " + seatIds.size() + " seat(s). " + soldCount + " SOLD seat(s) skipped."
                    : "Updated " + seatIds.size() + " seat(s).";
            loadSeatsAsync(zoneId);
        });
        task.setOnFailed(e -> {
            toggleInProgress = false;
            LOGGER.error("Mark unavailable failed", task.getException());
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            pendingToggleNote = "Error updating seats. Please try again.";
            loadSeatsAsync(zoneId);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleMarkAvailable() {
        if (toggleInProgress) return;

        List<Seat> selected = new ArrayList<>(seatsTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        List<Seat> eligible = selected.stream()
                .filter(s -> s.getStatus() == SeatStatus.DISABLED).toList();
        long soldCount = selected.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();

        if (eligible.isEmpty()) {
            seatsStatusLabel.setText(soldCount > 0
                    ? "SOLD seats skipped \u2014 no changes made"
                    : "No eligible seats in selection.");
            return;
        }

        List<Integer> seatIds = eligible.stream().map(Seat::getSeatId).toList();
        Zone selectedZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
        if (selectedZone == null) { seatsStatusLabel.setText("No zone selected."); return; }
        int zoneId = selectedZone.getZoneId();

        User capturedAdmin = currentAdminUser;
        if (capturedAdmin == null) { seatsStatusLabel.setText("Error: no active admin session."); return; }

        toggleInProgress = true;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SessionContext.setCurrentUser(capturedAdmin);
                try {
                    seatService.updateSeatStatus(seatIds, SeatStatus.AVAILABLE);
                } finally {
                    SessionContext.clearCurrentUser();
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            toggleInProgress = false;
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            pendingToggleNote = soldCount > 0
                    ? "Updated " + seatIds.size() + " seat(s). " + soldCount + " SOLD seat(s) skipped."
                    : "Updated " + seatIds.size() + " seat(s).";
            loadSeatsAsync(zoneId);
        });
        task.setOnFailed(e -> {
            toggleInProgress = false;
            LOGGER.error("Mark available failed", task.getException());
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            pendingToggleNote = "Error updating seats. Please try again.";
            loadSeatsAsync(zoneId);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleDeleteSeats() {
        List<Seat> selected = new ArrayList<>(seatsTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogThemeHelper.apply(confirm);
        confirm.setTitle("Delete Seats");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete " + selected.size() + " seat(s)? This cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            List<Integer> seatIds = new ArrayList<>();
            for (Seat s : selected) seatIds.add(s.getSeatId());
            Zone selectedZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            int zoneId = selectedZone != null ? selectedZone.getZoneId() : -1;
            User capturedAdmin = currentAdminUser;
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    SessionContext.setCurrentUser(capturedAdmin);
                    try {
                        seatService.deleteSeatsTransaction(seatIds);
                    } finally {
                        SessionContext.clearCurrentUser();
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                if (zoneId > 0) loadSeatsAsync(zoneId);
                Event currentEvent = zonesEventSelector.getSelectionModel().getSelectedItem();
                if (currentEvent != null) loadZonesAsync(currentEvent.getEventId());
            });
            task.setOnFailed(e -> {
                LOGGER.error("Delete seats failed", task.getException());
                Alert errAlert = new Alert(Alert.AlertType.ERROR);
                DialogThemeHelper.apply(errAlert);
                errAlert.setTitle("Delete Seats Failed");
                errAlert.setHeaderText(null);
                errAlert.setContentText("Error deleting seats. Please try again.");
                errAlert.showAndWait();
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    // ── Canvas rendering ──────────────────────────────────────────────────────

    private void renderSeatMap() {
        if (!isTabActive.get()) return;
        double canvasWidth  = seatMapCanvas.getWidth();
        double canvasHeight = seatMapCanvas.getHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0) return;

        List<Seat> seats = seatsViewModel.seatsProperty();
        seatCells.clear();

        if (seats.isEmpty()) {
            seatMapScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            seatMapScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            GraphicsContext gc = seatMapCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvasWidth, canvasHeight);
            gc.setFill(ThemePalette.placeholderText());
            gc.setFont(Font.font(13));
            if (seatsZoneSelector.getSelectionModel().getSelectedItem() == null) {
                gc.fillText("Select a zone above", PADDING, 30);
            } else {
                gc.fillText("No seats configured for this zone", PADDING, 30);
            }
            return;
        }

        seatMapScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        seatMapScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        LinkedHashMap<String, List<Seat>> byRow = new LinkedHashMap<>();
        for (Seat s : seats) {
            byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
        }

        int maxRowLength = byRow.values().stream().mapToInt(List::size).max().orElse(0);
        double requiredWidth  = Math.min(PADDING + maxRowLength * (CELL_SIZE + CELL_GAP) + PADDING, 8192.0);
        double requiredHeight = Math.min(PADDING + byRow.size() * (CELL_SIZE + ROW_GAP) + PADDING, 8192.0);
        boolean resized = false;
        if (canvasWidth < requiredWidth) {
            seatMapCanvas.setWidth(requiredWidth);
            resized = true;
        }
        if (canvasHeight < requiredHeight) {
            seatMapCanvas.setHeight(requiredHeight);
            resized = true;
        }
        if (resized) {
            seatMapCanvas.setVisible(false);
            Platform.runLater(() -> {
                seatMapCanvas.setVisible(true);
                Platform.runLater(this::renderSeatMap);
            });
            return;
        }

        GraphicsContext gc = seatMapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        Text textMeasurer = new Text();
        textMeasurer.setFont(Font.font(10));
        double yOffset = PADDING;
        for (Map.Entry<String, List<Seat>> entry : byRow.entrySet()) {
            double xOffset = PADDING;
            for (Seat seat : entry.getValue()) {
                Color fill = ThemePalette.seatFill(seat.getStatus());
                gc.setFill(fill);
                gc.fillRoundRect(xOffset, yOffset, CELL_SIZE, CELL_SIZE, 4, 4);
                gc.setFill(ThemePalette.seatLabelText());
                gc.setFont(Font.font(10));
                String label = seat.getSeatNumber() != null ? seat.getSeatNumber() : "";
                textMeasurer.setText(label);
                double textWidth = textMeasurer.getBoundsInLocal().getWidth();
                gc.fillText(label,
                        xOffset + (CELL_SIZE - textWidth) / 2.0,
                        yOffset + CELL_SIZE / 2.0 + 4);
                seatCells.add(new SeatCell(seat, xOffset, yOffset));
                if (seatsTable.getSelectionModel().getSelectedItems().contains(seat)) {
                    gc.setStroke(ThemePalette.selectionOutline());
                    gc.setLineWidth(2.0);
                    gc.strokeRoundRect(xOffset + 1, yOffset + 1, CELL_SIZE - 2, CELL_SIZE - 2, 4, 4);
                }
                xOffset += CELL_SIZE + CELL_GAP;
            }
            yOffset += CELL_SIZE + ROW_GAP;
        }
    }

    private SeatCell findCellAt(double x, double y) {
        for (SeatCell cell : seatCells) {
            if (x >= cell.x() && x <= cell.x() + CELL_SIZE
                    && y >= cell.y() && y <= cell.y() + CELL_SIZE) {
                return cell;
            }
        }
        return null;
    }

    private void handleCanvasMouseMoved(MouseEvent event) {
        SeatCell cell = findCellAt(event.getX(), event.getY());
        if (cell != null) {
            seatMapHoverLabel.setText("Row: " + cell.seat().getRowNumber()
                    + ", Seat: " + cell.seat().getSeatNumber()
                    + ", Status: " + cell.seat().getStatus());
        } else {
            seatMapHoverLabel.setText("");
        }
    }

    private void handleCanvasMousePressed(MouseEvent event) {
        SeatCell cell = findCellAt(event.getX(), event.getY());
        if (cell == null) return;
        int idx = seatsViewModel.seatsProperty().indexOf(cell.seat());
        if (idx < 0) return;
        if (event.isControlDown() || event.isMetaDown()) {
            if (seatsTable.getSelectionModel().isSelected(idx)) {
                seatsTable.getSelectionModel().clearSelection(idx);
            } else {
                seatsTable.getSelectionModel().select(idx);
            }
        } else {
            seatsTable.getSelectionModel().clearAndSelect(idx);
        }
    }

    private void handleCanvasMouseDragged(MouseEvent event) {
        SeatCell cell = findCellAt(event.getX(), event.getY());
        if (cell == null) return;
        int idx = seatsViewModel.seatsProperty().indexOf(cell.seat());
        if (idx >= 0 && !seatsTable.getSelectionModel().isSelected(idx)) {
            seatsTable.getSelectionModel().select(idx);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FXMLLoader createZoneFormLoader() {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("ZoneFormView.fxml"));
            loader.load();
            return loader;
        } catch (IOException ex) {
            LOGGER.error("Failed to load ZoneFormView.fxml", ex);
            showErrorAlert("Internal Error", "Could not open the zone form. Please try again.");
            return null;
        }
    }

    private Dialog<ButtonType> buildZoneDialog(String title, Parent content) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogThemeHelper.apply(dialog);
        return dialog;
    }

    private void showGenerateError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        DialogThemeHelper.apply(alert);
        alert.setTitle("Generate Seats Failed");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateGenerateButtonState() {
        boolean zoneSelected = seatsZoneSelector.getSelectionModel().getSelectedItem() != null;
        boolean rowFilled = seatsRowField.getText() != null && !seatsRowField.getText().isBlank();
        boolean fromValid = isPositiveInt(seatsFromField.getText());
        boolean toValid   = isPositiveInt(seatsToField.getText());
        boolean rangeValid = fromValid && toValid
                && Integer.parseInt(seatsFromField.getText()) <= Integer.parseInt(seatsToField.getText());
        seatsGenerateButton.setDisable(!(zoneSelected && rowFilled && rangeValid));
    }

    private boolean isPositiveInt(String text) {
        try {
            return text != null && !text.isBlank() && Integer.parseInt(text) >= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDuplicateError(Throwable ex) {
        if (ex instanceof java.sql.SQLException sqle) {
            return "23505".equals(sqle.getSQLState());
        }
        if (ex != null && ex.getCause() instanceof java.sql.SQLException sqle) {
            return "23505".equals(sqle.getSQLState());
        }
        String msg = ex != null ? ex.getMessage() : null;
        return msg != null && (msg.contains("duplicate")
                || msg.contains("unique constraint") || msg.contains("23505"));
    }

    private void showSecurityAwareError(String baseTitle, Throwable ex) {
        String title = (ex instanceof SecurityException) ? "Access Denied" : baseTitle;
        String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                : "Operation failed. Please try again.";
        showErrorAlert(title, msg);
    }

    private void showErrorAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            DialogThemeHelper.apply(alert);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
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
