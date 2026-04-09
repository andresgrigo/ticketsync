package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import com.ticketsync.model.Zone;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.EventService;
import com.ticketsync.service.SeatService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.service.UserManagementService;
import com.ticketsync.service.ZoneService;
import com.ticketsync.viewmodel.EventManagementViewModel;
import com.ticketsync.viewmodel.SeatManagementViewModel;
import com.ticketsync.viewmodel.UserManagementViewModel;
import com.ticketsync.viewmodel.ZoneManagementViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.util.Pair;

/**
 * FXML controller for the admin dashboard view
 * ({@code AdminDashboardView.fxml}).
 *
 * <p>
 * Provides a tabbed interface for managing users. The Users tab displays
 * a {@link TableView} backed by a {@link UserManagementViewModel}; CRUD
 * operations are performed through {@link UserManagementService} on background
 * daemon threads so the FX application thread is never blocked.
 *
 * <p>
 * Role-based access control is enforced in {@link #initialize()}: if the
 * current session user is absent or not an ADMIN the controller logs an error
 * and navigates back to the login screen.
 */
public class AdminDashboardController {

    private static final Logger LOGGER = LogManager.getLogger(AdminDashboardController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserManagementService userService = new UserManagementService();
    private final EventService eventService = new EventService();
    private UserManagementViewModel viewModel;
    private EventManagementViewModel eventsViewModel;
    private User currentAdminUser;

    @FXML
    private Label loggedInUserLabel;
    @FXML
    private Button logoutButton;
    @FXML
    private TableView<User> usersTable;
    @FXML
    private TableColumn<User, String> usernameColumn;
    @FXML
    private TableColumn<User, String> roleColumn;
    @FXML
    private TableColumn<User, String> createdAtColumn;
    @FXML
    private Button createUserButton;
    @FXML
    private Button editUserButton;
    @FXML
    private Button deleteUserButton;
    @FXML
    private Label statusLabel;

    @FXML
    private TableView<Event> eventsTable;
    @FXML
    private TableColumn<Event, String> eventNameColumn;
    @FXML
    private TableColumn<Event, String> eventDateColumn;
    @FXML
    private TableColumn<Event, String> eventVenueColumn;
    @FXML
    private TableColumn<Event, String> eventDescColumn;
    @FXML
    private TableColumn<Event, String> eventStatusColumn;
    @FXML
    private Button createEventButton;
    @FXML
    private Button editEventButton;
    @FXML
    private Button deleteEventButton;
    @FXML
    private Button toggleActivateButton;
    @FXML
    private Label eventsStatusLabel;

    @FXML
    private TableView<Zone> zonesTable;
    @FXML
    private TableColumn<Zone, String> zonesNameColumn;
    @FXML
    private TableColumn<Zone, String> zonesPriceColumn;
    @FXML
    private TableColumn<Zone, String> zonesSeatCountColumn;
    @FXML
    private ComboBox<Event> zonesEventSelector;
    @FXML
    private Button zonesAddButton;
    @FXML
    private Button zonesEditButton;
    @FXML
    private Button zonesDeleteButton;
    @FXML
    private Label zonesStatusLabel;

    private ZoneManagementViewModel zonesViewModel;
    private final ZoneService zoneService = new ZoneService();
    private Map<Integer, Integer> zoneSeatCounts = new HashMap<>();

    @FXML
    private TableView<Seat> seatsTable;
    @FXML
    private TableColumn<Seat, String> seatsRowColumn;
    @FXML
    private TableColumn<Seat, String> seatsSeatColumn;
    @FXML
    private TableColumn<Seat, String> seatsStatusColumn;
    @FXML
    private ComboBox<Zone> seatsZoneSelector;
    @FXML
    private TextField seatsRowField;
    @FXML
    private TextField seatsFromField;
    @FXML
    private TextField seatsToField;
    @FXML
    private Button seatsGenerateButton;
    @FXML
    private Button seatsDeleteButton;
    @FXML
    private Tab seatingTab;
    @FXML
    private Canvas seatMapCanvas;
    @FXML
    private ScrollPane seatMapScrollPane;
    @FXML
    private Label seatMapHoverLabel;
    @FXML
    private Button seatsMarkUnavailableButton;
    @FXML
    private Button seatsMarkAvailableButton;
    @FXML
    private Label seatsStatusLabel;

    private SeatManagementViewModel seatsViewModel;
    private final SeatService seatService = new SeatService();
    /** Pending note to append to the count summary when the next loadSeatsAsync completes. */
    private String pendingToggleNote;
    /** Re-entrancy guard for the seat toggle handlers. */
    private boolean toggleInProgress;

    private static final int CELL_SIZE = 32;
    private static final int CELL_GAP = 4;
    private static final int ROW_GAP = 8;
    private static final int PADDING = 10;

    private record SeatCell(Seat seat, double x, double y) {
    }

    private List<SeatCell> seatCells = new ArrayList<>();

    // ── Layout View tab ──────────────────────────────────────────────
    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab layoutViewTab;
    @FXML
    private ComboBox<Event> layoutEventSelector;
    @FXML
    private Button layoutExportButton;
    @FXML
    private Label layoutEventNameLabel;
    @FXML
    private Label layoutTotalLabel;
    @FXML
    private Label layoutAvailableLabel;
    @FXML
    private Label layoutSoldLabel;
    @FXML
    private Label layoutDisabledLabel;
    @FXML
    private Canvas layoutCanvas;
    @FXML
    private ScrollPane layoutScrollPane;
    @FXML
    private Label layoutHoverLabel;

    // Layout view state
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

    private static final int LAYOUT_CELL_SIZE       = 48;  // UX-DR13: 48×48px minimum
    private static final int LAYOUT_CELL_GAP        = 6;
    private static final int LAYOUT_ROW_GAP         = 8;
    private static final int LAYOUT_ZONE_GAP        = 30;  // vertical gap between zone sections
    private static final int LAYOUT_PADDING         = 16;
    private static final int LAYOUT_ROW_LABEL_WIDTH = 64;  // px reserved for row name column
    private static final int LAYOUT_MAX_SEATS_ROW   = 18;  // max seats shown per row

    /**
     * Initialises the controller after FXML injection.
     *
     * <ol>
     * <li>Reads the current session from {@link SessionContext}.</li>
     * <li>Enforces ADMIN role (AC: 12).</li>
     * <li>Configures table columns and button bindings.</li>
     * <li>Loads users asynchronously.</li>
     * </ol>
     */
    @FXML
    public void initialize() {
        Optional<User> opt = SessionContext.getCurrentUser();
        if (opt.isEmpty()) {
            LOGGER.error("AdminDashboardController.initialize() — no session user; redirecting to login");
            navigateToLogin();
            return;
        }

        if (!"ADMIN".equalsIgnoreCase(opt.get().getRole())) {
            LOGGER.error(
                    "AdminDashboardController.initialize() — user '{}' has role '{}', not ADMIN; redirecting to login",
                    opt.get().getUsername(), opt.get().getRole());
            navigateToLogin();
            return;
        }

        currentAdminUser = opt.get();
        loggedInUserLabel.setText("Logged in as: " + currentAdminUser.getUsername());

        viewModel = new UserManagementViewModel();

        // Configure TableView columns
        usernameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        roleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRole()));
        createdAtColumn.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getCreatedAt();
            String formatted = (dt != null) ? dt.format(DT_FMT) : "—";
            return new SimpleStringProperty(formatted);
        });

        usersTable.setItems(viewModel.usersProperty());

        // Bind selection to ViewModel
        viewModel.selectedUserProperty().bind(usersTable.getSelectionModel().selectedItemProperty());

        // Bind edit/delete buttons: disabled when no selection OR selected user is self
        // (AC: 10, 11)
        BooleanBinding noSelectionOrSelf = Bindings.createBooleanBinding(
                () -> {
                    User sel = usersTable.getSelectionModel().getSelectedItem();
                    if (sel == null)
                        return true;
                    return sel.getUserId() == currentAdminUser.getUserId();
                },
                usersTable.getSelectionModel().selectedItemProperty());
        editUserButton.disableProperty().bind(noSelectionOrSelf);
        deleteUserButton.disableProperty().bind(noSelectionOrSelf);

        loadUsersAsync();

        // ---- Event Management setup ----
        eventsViewModel = new EventManagementViewModel();

        eventNameColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getName() != null ? data.getValue().getName() : ""));

        eventDateColumn.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getEventDate();
            return new SimpleStringProperty(dt != null ? dt.format(DT_FMT) : "\u2014");
        });

        eventVenueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getVenue() != null
                ? data.getValue().getVenue()
                : "\u2014"));

        eventDescColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescription() != null
                ? data.getValue().getDescription()
                : ""));

        eventStatusColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().isActive() ? "Active" : "Inactive"));
        eventStatusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                    setStyle("");
                } else if ("Active".equals(val)) {
                    setText("Active");
                    setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                } else {
                    setText("Inactive");
                    setStyle("-fx-text-fill: #757575;");
                }
            }
        });

        eventsTable.setItems(eventsViewModel.eventsProperty());

        eventsViewModel.selectedEventProperty().bind(
                eventsTable.getSelectionModel().selectedItemProperty());

        BooleanBinding noEventSelection = eventsTable.getSelectionModel().selectedItemProperty().isNull();
        editEventButton.disableProperty().bind(noEventSelection);
        deleteEventButton.disableProperty().bind(noEventSelection);
        toggleActivateButton.disableProperty().bind(noEventSelection);

        toggleActivateButton.textProperty().bind(
                Bindings.createStringBinding(
                        () -> {
                            Event sel = eventsTable.getSelectionModel().getSelectedItem();
                            if (sel == null)
                                return "Activate";
                            return sel.isActive() ? "Deactivate" : "Activate";
                        },
                        eventsTable.getSelectionModel().selectedItemProperty()));

        loadEventsAsync();

        // ---- Zone Management setup ----
        zonesViewModel = new ZoneManagementViewModel();

        zonesTable.setItems(zonesViewModel.zonesProperty());

        zonesNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName() != null
                ? data.getValue().getName()
                : ""));
        zonesPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPrice() != null
                ? String.format("%.2f", data.getValue().getPrice())
                : "\u2014"));
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

        // ---- Zone event selector ----
        zonesEventSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Event item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        zonesEventSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Event item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select event…" : item.getName());
            }
        });
        zonesEventSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldEvent, newEvent) -> {
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
        loadZonesEventSelectorAsync();

        // ---- Seat Management setup ----
        seatsViewModel = new SeatManagementViewModel();
        seatsTable.setItems(seatsViewModel.seatsProperty());
        seatsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        seatsRowColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRowNumber() != null
                ? data.getValue().getRowNumber()
                : ""));
        seatsSeatColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSeatNumber() != null
                ? data.getValue().getSeatNumber()
                : ""));
        seatsStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus() != null
                ? data.getValue().getStatus().name()
                : ""));
        seatsStatusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(val);
                    switch (val) {
                        case "AVAILABLE" -> setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                        case "SOLD" -> setStyle("-fx-text-fill: #C62828; -fx-font-weight: bold;");
                        case "DISABLED" -> setStyle("-fx-text-fill: #757575;");
                        default -> setStyle("");
                    }
                }
            }
        });

        seatsZoneSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Zone item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        seatsZoneSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Zone item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select zone\u2026" : item.getName());
            }
        });

        seatsDeleteButton.disableProperty().bind(
                seatsTable.getSelectionModel().selectedItemProperty().isNull());

        // AC1/AC2/AC5: each toggle button disabled unless the selection contains an eligible seat
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

        // Wire input listeners for generate button enablement
        seatsRowField.textProperty().addListener((obs, o, n) -> updateGenerateButtonState());
        seatsFromField.textProperty().addListener((obs, o, n) -> updateGenerateButtonState());
        seatsToField.textProperty().addListener((obs, o, n) -> updateGenerateButtonState());
        seatsZoneSelector.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> updateGenerateButtonState());

        // TextFormatter: digits only for From and To fields
        seatsFromField.setTextFormatter(new TextFormatter<>(
                change -> change.getControlNewText().matches("\\d*") ? change : null));
        seatsToField.setTextFormatter(new TextFormatter<>(
                change -> change.getControlNewText().matches("\\d*") ? change : null));

        // TextFormatter: max 10 characters for Row field (matches DB varchar(10))
        seatsRowField.setTextFormatter(new TextFormatter<>(
                change -> change.getControlNewText().length() <= 10 ? change : null));

        // Load seats when zone is selected in seatsZoneSelector
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

        // AC9: sync zone TableView selection to seatsZoneSelector
        zonesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newZone) -> {
            if (newZone != null) {
                seatsZoneSelector.getItems().stream()
                        .filter(z -> z.getZoneId() == newZone.getZoneId())
                        .findFirst()
                        .ifPresent(z -> seatsZoneSelector.getSelectionModel().select(z));
            }
        });

        seatMapCanvas.setOnMouseMoved(this::handleCanvasMouseMoved);
        seatMapCanvas.setOnMousePressed(this::handleCanvasMousePressed);
        seatMapCanvas.setOnMouseDragged(this::handleCanvasMouseDragged);
        seatsTable.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<Seat>) change -> Platform.runLater(this::renderSeatMap));

        // ── Layout View tab setup ────────────────────────────────────────
        layoutEventSelector.setConverter(new javafx.util.StringConverter<Event>() {
            @Override
            public String toString(Event e) {
                return (e == null) ? "" : e.getName();
            }
            @Override
            public Event fromString(String s) { return null; }
        });
        layoutEventSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Event item, boolean empty) {
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

        // Load events into layout selector (same source as zonesEventSelector)
        loadLayoutEventsAsync();

        // Refresh layout when the tab is selected so zone/seat changes are visible
        mainTabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> {
                    if (newTab == seatingTab) {
                        Platform.runLater(this::renderSeatMap);
                    } else if (newTab == layoutViewTab) {
                        Event selectedEvent = layoutEventSelector.getSelectionModel().getSelectedItem();
                        if (selectedEvent != null) {
                            // Double-defer: first frame allocates the RTTexture, second draws.
                            Platform.runLater(() -> loadLayoutViewAsync(selectedEvent));
                        }
                    }
                });
    }

    /**
     * Loads all users from the database on a background thread and populates
     * the {@link UserManagementViewModel} on the FX thread when complete.
     *
     * <p>
     * Sets the status label to "Loading…" while the operation is in
     * progress and clears it on success. On failure an error message is
     * displayed in the status label.
     */
    private void loadUsersAsync() {
        statusLabel.setText("Loading...");
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() throws Exception {
                return userService.getAllUsers();
            }
        };
        task.setOnSucceeded(e -> {
            viewModel.setUsers(task.getValue());
            statusLabel.setText("");
        });
        task.setOnFailed(e -> {
            LOGGER.error("Failed to load users", task.getException());
            statusLabel.setText("Error loading users");
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Handles the "Create User" button action (AC: 4, 5, 6).
     *
     * <p>
     * Opens {@code UserFormView.fxml} in a dialog. An event filter on the
     * OK button calls {@link UserFormController#validate()} before allowing
     * the dialog to close, so invalid input is surfaced without dismissing
     * the dialog.
     */
    @FXML
    private void handleCreateUser() {
        FXMLLoader loader = createFXMLLoader();
        if (loader == null)
            return;

        UserFormController formController = loader.getController();
        formController.setMode(UserFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = buildFormDialog("Create User", (Parent) loader.getRoot(), formController);

        // Second event filter: inline username uniqueness check (AC: 5).
        // Fires only after the primary validate() filter passes (event not yet
        // consumed).
        Button createOkBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        createOkBtn.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (userService.usernameExists(formController.getUsername())) {
                    formController.showExternalError("Username is already taken");
                    event.consume();
                }
            } catch (SQLException ex) {
                LOGGER.error("Username uniqueness check failed", ex);
                // Fall through — the Task will catch the duplicate key constraint instead
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String username = formController.getUsername();
            String password = formController.getPassword();
            String role = formController.getSelectedRole();
            Task<Integer> task = new Task<>() {
                @Override
                protected Integer call() throws Exception {
                    return userService.createUser(username, password, role, currentAdminUser.getUsername());
                }
            };
            task.setOnSucceeded(e -> loadUsersAsync());
            task.setOnFailed(e -> {
                LOGGER.error("Create user failed", task.getException());
                String msg = task.getException() != null
                        ? task.getException().getMessage()
                        : "Operation failed. Please try again.";
                showErrorAlert("Create User Failed", msg);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Edit User" button action (AC: 7, 8).
     *
     * <p>
     * Pre-populates the form with the selected user's data (username
     * read-only, password fields hidden). On confirmation the user's role
     * is updated via {@link UserManagementService#updateUserRole}.
     */
    @FXML
    private void handleEditUser() {
        User selectedUser = viewModel.selectedUserProperty().get();
        if (selectedUser == null)
            return;

        FXMLLoader loader = createFXMLLoader();
        if (loader == null)
            return;

        UserFormController formController = loader.getController();
        formController.setMode(UserFormController.Mode.EDIT);
        formController.setUser(selectedUser);

        Dialog<ButtonType> dialog = buildFormDialog("Edit User", (Parent) loader.getRoot(), formController);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String newRole = formController.getSelectedRole();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    userService.updateUserRole(selectedUser, newRole, currentAdminUser.getUsername());
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadUsersAsync());
            task.setOnFailed(e -> {
                LOGGER.error("Update user role failed", task.getException());
                showErrorAlert("Edit User Failed", "Operation failed. Please try again.");
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Delete User" button action (AC: 9).
     *
     * <p>
     * Shows a confirmation alert before permanently deleting the selected
     * user from the database.
     */
    @FXML
    private void handleDeleteUser() {
        User selectedUser = viewModel.selectedUserProperty().get();
        if (selectedUser == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete User");
        confirm.setHeaderText(null);
        confirm.setContentText("Are you sure you want to delete user '" + selectedUser.getUsername() + "'?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    userService.deleteUser(
                            selectedUser.getUserId(),
                            selectedUser.getUsername(),
                            currentAdminUser.getUsername());
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadUsersAsync());
            task.setOnFailed(e -> {
                LOGGER.error("Delete user failed", task.getException());
                showErrorAlert("Delete User Failed", "Operation failed. Please try again.");
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Logout" button action (AC: 13).
     *
     * <p>
     * Clears the current session and navigates to the login screen.
     */
    @FXML
    private void handleLogout() {
        try {
            new AuthenticationService().logout();
            App.setRoot("LoginView");
        } catch (IOException ex) {
            LOGGER.error("Failed to navigate to LoginView on logout", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Event Management handlers
    // -------------------------------------------------------------------------

    /**
     * Loads all events from the database on a background daemon thread and
     * populates the {@link EventManagementViewModel} on the FX thread when
     * complete (AC: 1, 12).
     *
     * <p>
     * Sets the status label to "Loading…" during the operation and clears
     * it on success. On failure an error message is displayed.
     */
    private void loadEventsAsync() {
        eventsStatusLabel.setText("Loading...");
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
            eventsViewModel.setEvents(task.getValue());
            eventsStatusLabel.setText("");
        });
        task.setOnFailed(e -> {
            LOGGER.error("Failed to load events", task.getException());
            eventsStatusLabel.setText("Error loading events");
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Handles the "Create Event" button action (AC: 3, 4, 5).
     *
     * <p>
     * Opens {@code EventFormView.fxml} in a dialog. An event filter on
     * the OK button calls {@link EventFormController#validate()} before
     * allowing the dialog to close. On confirmation the event is created via
     * {@link EventService#createEvent(Event)} on a background thread.
     */
    @FXML
    private void handleCreateEvent() {
        FXMLLoader loader = createEventFXMLLoader();
        if (loader == null)
            return;

        EventFormController formController = loader.getController();
        formController.setMode(EventFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Event");
        dialog.getDialogPane().setContent((Parent) loader.getRoot());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (!formController.validate())
                event.consume();
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Event newEvent = formController.getEventFromForm();
            User capturedAdmin = currentAdminUser;
            Task<Integer> task = new Task<>() {
                @Override
                protected Integer call() throws Exception {
                    SessionContext.setCurrentUser(capturedAdmin);
                    try {
                        return eventService.createEvent(newEvent);
                    } finally {
                        SessionContext.clearCurrentUser();
                    }
                }
            };
            task.setOnSucceeded(e -> loadEventsAsync());
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                LOGGER.error("Create event failed", ex);
                String title = (ex instanceof SecurityException) ? "Access Denied" : "Create Event Failed";
                String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                        : "Operation failed. Please try again.";
                showErrorAlert(title, msg);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Edit Event" button action (AC: 6, 7).
     *
     * <p>
     * Pre-populates the event form with the selected event's data.
     * On confirmation the event is updated via
     * {@link EventService#updateEvent(Event)}, preserving identity fields.
     */
    @FXML
    private void handleEditEvent() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null)
            return;

        FXMLLoader loader = createEventFXMLLoader();
        if (loader == null)
            return;

        EventFormController formController = loader.getController();
        formController.setMode(EventFormController.Mode.EDIT);
        formController.setEvent(selectedEvent);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Event");
        dialog.getDialogPane().setContent((Parent) loader.getRoot());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (!formController.validate())
                event.consume();
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Event updatedEvent = formController.getEventFromForm();
            User capturedAdmin = currentAdminUser;
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    SessionContext.setCurrentUser(capturedAdmin);
                    try {
                        eventService.updateEvent(updatedEvent);
                    } finally {
                        SessionContext.clearCurrentUser();
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadEventsAsync());
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                LOGGER.error("Edit event failed", ex);
                String title = (ex instanceof SecurityException) ? "Access Denied" : "Edit Event Failed";
                String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                        : "Operation failed. Please try again.";
                showErrorAlert(title, msg);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Delete Event" button action (AC: 8).
     *
     * <p>
     * Shows a confirmation alert before permanently deleting the selected
     * event and all associated seating data.
     */
    @FXML
    private void handleDeleteEvent() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Event");
        confirm.setHeaderText(null);
        String eventName = selectedEvent.getName() != null ? selectedEvent.getName() : "(unnamed)";
        confirm.setContentText("Delete event '" + eventName
                + "'? This will remove all associated seating.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            User capturedAdmin = currentAdminUser;
            int eventId = selectedEvent.getEventId();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    SessionContext.setCurrentUser(capturedAdmin);
                    try {
                        eventService.deleteEvent(eventId);
                    } finally {
                        SessionContext.clearCurrentUser();
                    }
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadEventsAsync());
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                LOGGER.error("Delete event failed", ex);
                String title = (ex instanceof SecurityException) ? "Access Denied" : "Delete Event Failed";
                String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                        : "Operation failed. Please try again.";
                showErrorAlert(title, msg);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Activate" / "Deactivate" toggle button action (AC: 9, 11).
     *
     * <p>
     * Calls {@link EventService#activateEvent(int)} or
     * {@link EventService#deactivateEvent(int)} depending on the selected
     * event's current active state, then refreshes the table.
     */
    @FXML
    private void handleToggleActivate() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null)
            return;

        boolean currentlyActive = selectedEvent.isActive();
        int eventId = selectedEvent.getEventId();
        User capturedAdmin = currentAdminUser;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                SessionContext.setCurrentUser(capturedAdmin);
                try {
                    if (currentlyActive) {
                        eventService.deactivateEvent(eventId);
                    } else {
                        eventService.activateEvent(eventId);
                    }
                } finally {
                    SessionContext.clearCurrentUser();
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> loadEventsAsync());
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            LOGGER.error("Toggle activate event failed", ex);
            String title = (ex instanceof SecurityException) ? "Access Denied" : "Operation Failed";
            String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                    : "Operation failed. Please try again.";
            showErrorAlert(title, msg);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads {@code UserFormView.fxml} and returns the initialised
     * {@link FXMLLoader} so callers can retrieve both the root node and
     * the controller.
     *
     * @return the loaded {@link FXMLLoader}, or {@code null} on I/O error
     */
    private FXMLLoader createFXMLLoader() {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("UserFormView.fxml"));
            loader.load();
            return loader;
        } catch (IOException ex) {
            LOGGER.error("Failed to load UserFormView.fxml", ex);
            showErrorAlert("Internal Error", "Could not open the user form. Please try again.");
            return null;
        }
    }

    /**
     * Loads {@code EventFormView.fxml} and returns the initialised
     * {@link FXMLLoader} so callers can retrieve both the root node and
     * the {@link EventFormController}.
     *
     * @return the loaded {@link FXMLLoader}, or {@code null} on I/O error
     */
    private FXMLLoader createEventFXMLLoader() {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("EventFormView.fxml"));
            loader.load(); // return value is already accessible via loader.getRoot()
            return loader;
        } catch (IOException ex) {
            LOGGER.error("Failed to load EventFormView.fxml", ex);
            showErrorAlert("Internal Error", "Could not open the event form. Please try again.");
            return null;
        }
    }

    /**
     * Constructs a {@link Dialog} with the supplied form root as content and
     * wires an event filter to the OK button that prevents the dialog from
     * closing if {@link UserFormController#validate()} returns {@code false}.
     *
     * @param title          the dialog window title
     * @param formContent    the FXML root node to embed as dialog content
     * @param formController the controller responsible for validation
     * @return the configured dialog, ready to call {@code showAndWait()} on
     */
    private Dialog<ButtonType> buildFormDialog(String title, Parent formContent,
            UserFormController formController) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(formContent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (!formController.validate()) {
                event.consume();
            }
        });
        return dialog;
    }

    /**
     * Displays an error {@link Alert} on the FX thread.
     *
     * @param title   the alert window title
     * @param message the error message to display
     */
    private void showErrorAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Navigates to the login view.
     *
     * <p>
     * Called when RBAC enforcement in {@link #initialize()} fails so that
     * the admin dashboard is never shown to non-admin users (AC: 12).
     */
    private void navigateToLogin() {
        try {
            App.setRoot("LoginView");
        } catch (IOException ex) {
            LOGGER.error("Failed to navigate to LoginView", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Zone Management handlers
    // -------------------------------------------------------------------------

    /**
     * Loads all events into the Seating tab event selector on a background thread.
     */
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
            // Restore previous selection if it still exists
            if (previousSelection != null) {
                task.getValue().stream()
                        .filter(ev -> ev.getEventId() == previousSelection.getEventId())
                        .findFirst()
                        .ifPresent(ev -> zonesEventSelector.getSelectionModel().select(ev));
            }
        });
        task.setOnFailed(e -> LOGGER.error("Failed to load events for zone selector", task.getException()));
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

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
                layoutEventSelector.getItems().stream()
                        .filter(ev -> ev.getEventId() == previousSelection.getEventId())
                        .findFirst()
                        .ifPresent(ev -> layoutEventSelector.getSelectionModel().select(ev));
            }
        });
        task.setOnFailed(e -> LOGGER.error("Failed to load events for layout selector", task.getException()));
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
            // Discard results if user changed event selection while this task was in flight
            Event currentSelection = layoutEventSelector.getSelectionModel().getSelectedItem();
            if (currentSelection == null || currentSelection.getEventId() != event.getEventId()) return;

            List<Seat> seats = task.getValue().getKey();
            List<Zone> zones = task.getValue().getValue();

            // Build zoneId → Zone lookup map
            Map<Integer, Zone> zoneMap = zones.stream()
                    .collect(java.util.stream.Collectors.toMap(Zone::getZoneId, z -> z));

            // Update stats context panel (AC3)
            long available = seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
            long sold      = seats.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();
            long disabled  = seats.stream().filter(s -> s.getStatus() == SeatStatus.DISABLED).count();
            layoutEventNameLabel.setText(event.getName());
            layoutTotalLabel.setText("Total: " + seats.size());
            layoutAvailableLabel.setText("Available: " + available);
            layoutSoldLabel.setText("Sold: " + sold);
            layoutDisabledLabel.setText("Disabled: " + disabled);

            // Reset zoom/pan on new event load (AC5)
            layoutZoom = 1.0;
            layoutPanX = 0;
            layoutPanY = 0;

            // Store for re-rendering
            layoutCurrentSeats = seats;
            layoutCurrentZoneMap = zoneMap;

            renderLayoutView(seats, zoneMap);
        });
        task.setOnFailed(e -> {
            LOGGER.error("Failed to load layout view for event {}", event.getEventId(), task.getException());
            layoutHoverLabel.setText("Error loading layout. Please try again.");
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

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

    /**
     * Renders the read-only layout view canvas with all zones and seats for the selected event.
     * Zones are displayed sequentially (top to bottom), each preceded by a 16px bold zone label.
     * Seats are 48×48px (UX-DR13). Zoom/pan transform applied via GraphicsContext.
     */
    private void renderLayoutView(List<Seat> allSeats, Map<Integer, Zone> zoneMap) {
        // Guard: skip when the Layout View tab is not selected — the prism RTTexture
        // for the canvas is only allocated after the tab has been shown at least once.
        if (mainTabPane.getSelectionModel().getSelectedItem() != layoutViewTab)
            return;
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

        // Group seats by zone preserving zone order
        LinkedHashMap<Integer, List<Seat>> byZone = new LinkedHashMap<>();
        for (Seat s : allSeats) {
            byZone.computeIfAbsent(s.getZoneId(), k -> new ArrayList<>()).add(s);
        }

        // Compute world-space layout dimensions
        double worldHeight = LAYOUT_PADDING;
        for (Map.Entry<Integer, List<Seat>> entry : byZone.entrySet()) {
            worldHeight += 24; // zone label height
            worldHeight += 8;  // gap below label
            java.util.TreeMap<String, List<Seat>> byRow = new java.util.TreeMap<>(AdminDashboardController::numericStringCompare);
            for (Seat s : entry.getValue()) {
                byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
            }
            worldHeight += byRow.size() * (LAYOUT_CELL_SIZE + LAYOUT_ROW_GAP);
            worldHeight += LAYOUT_ZONE_GAP;
        }
        worldHeight += LAYOUT_PADDING;

        int maxSeatsPerRow = (int) allSeats.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s.getZoneId() + ":" + s.getRowNumber(),
                        java.util.stream.Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).max().orElse(0L);
        double worldWidth = LAYOUT_PADDING + LAYOUT_ROW_LABEL_WIDTH + maxSeatsPerRow * (LAYOUT_CELL_SIZE + LAYOUT_CELL_GAP) + LAYOUT_PADDING;

        // Resize canvas to fit zoomed content.
        // Cap at 8192 to stay within OpenGL min-guaranteed max texture size.
        // Hide the canvas BEFORE resizing: when theCanvas buffer is non-null (canvas was
        // drawn before), Prism calls initCanvas on any size change and tries to reallocate
        // the RTTexture — which can return null, causing NGCanvas NPE. Invisible nodes are
        // skipped by Prism entirely, so no RTTexture allocation happens during the transition.
        final double MAX_TEX = 8192.0;
        double scaledW = Math.min(Math.max(canvasW, worldWidth  * layoutZoom + Math.abs(layoutPanX)), MAX_TEX);
        double scaledH = Math.min(Math.max(canvasH, worldHeight * layoutZoom + Math.abs(layoutPanY)), MAX_TEX);
        boolean layoutResized = false;
        if (layoutCanvas.getWidth() < scaledW) {
            layoutCanvas.setWidth(scaledW);
            layoutResized = true;
        }
        if (layoutCanvas.getHeight() < scaledH) {
            layoutCanvas.setHeight(scaledH);
            layoutResized = true;
        }
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

        // Apply zoom + pan transform
        gc.save();
        gc.translate(layoutPanX, layoutPanY);
        gc.scale(layoutZoom, layoutZoom);

        Text measurer = new Text();
        double yOffset = LAYOUT_PADDING;

        for (Map.Entry<Integer, List<Seat>> zoneEntry : byZone.entrySet()) {
            Zone zone = zoneMap.get(zoneEntry.getKey());
            String zoneName = (zone != null) ? zone.getName() : "Zone " + zoneEntry.getKey();

            // Zone label — 16px bold (UX spec)
            gc.setFill(Color.web("#212121"));
            gc.setFont(Font.font(null, FontWeight.BOLD, 16));
            gc.fillText(zoneName, LAYOUT_PADDING, yOffset + 16);
            yOffset += 24 + 8;

            // Group by row
            java.util.TreeMap<String, List<Seat>> byRow = new java.util.TreeMap<>(AdminDashboardController::numericStringCompare);
            for (Seat s : zoneEntry.getValue()) {
                byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
            }

            for (Map.Entry<String, List<Seat>> rowEntry : byRow.entrySet()) {
                double xOffset = LAYOUT_PADDING;
                // Row label
                gc.setFill(Color.web("#616161"));
                gc.setFont(Font.font(11));
                gc.fillText(rowEntry.getKey(), xOffset, yOffset + LAYOUT_CELL_SIZE / 2.0 + 4);
                xOffset += LAYOUT_ROW_LABEL_WIDTH;

                List<Seat> rowSeats = new ArrayList<>(rowEntry.getValue());
                rowSeats.sort((a, b) -> numericStringCompare(a.getSeatNumber(), b.getSeatNumber()));
                for (Seat seat : rowSeats) {
                    Color fill = switch (seat.getStatus()) {
                        case AVAILABLE -> Color.web("#4CAF50");
                        case SOLD      -> Color.web("#F44336");
                        case DISABLED  -> Color.web("#9E9E9E");
                        default        -> Color.web("#9E9E9E");
                    };
                    gc.setFill(fill);
                    gc.fillRoundRect(xOffset, yOffset, LAYOUT_CELL_SIZE, LAYOUT_CELL_SIZE, 6, 6);

                    // Seat number label (center-aligned, white)
                    gc.setFill(Color.WHITE);
                    gc.setFont(Font.font(11));
                    String label = seat.getSeatNumber() != null ? seat.getSeatNumber() : "";
                    measurer.setText(label);
                    measurer.setFont(Font.font(11));
                    double textW = measurer.getBoundsInLocal().getWidth();
                    gc.fillText(label,
                            xOffset + (LAYOUT_CELL_SIZE - textW) / 2.0,
                            yOffset + LAYOUT_CELL_SIZE / 2.0 + 4);

                    // Store world-space cell for hit testing (before transform)
                    layoutSeatCells.add(new LayoutSeatCell(seat, zone, xOffset, yOffset));
                    xOffset += LAYOUT_CELL_SIZE + LAYOUT_CELL_GAP;
                }
                yOffset += LAYOUT_CELL_SIZE + LAYOUT_ROW_GAP;
            }
            yOffset += LAYOUT_ZONE_GAP;
        }

        gc.restore(); // undo zoom/pan transform
    }

    /**
     * Loads zones and seat counts for the specified event on a background daemon
     * thread and populates the {@link ZoneManagementViewModel} on the FX thread.
     *
     * @param eventId the event whose zones should be loaded
     */
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
            // Also populate seatsZoneSelector, restoring previous selection if possible
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

    /**
     * Handles the "+ Add Zone" button action (AC: 2, 7).
     */
    @FXML
    private void handleAddZone() {
        Event selectedEvent = zonesEventSelector.getSelectionModel().getSelectedItem();
        if (selectedEvent == null)
            return;

        FXMLLoader loader = createZoneFXMLLoader();
        if (loader == null)
            return;

        ZoneFormController formController = loader.getController();
        formController.setMode(ZoneFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Zone");
        dialog.getDialogPane().setContent((Parent) loader.getRoot());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (!formController.validate())
                event.consume();
        });

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
                Throwable ex = task.getException();
                LOGGER.error("Add zone failed", ex);
                String title = (ex instanceof SecurityException) ? "Access Denied" : "Add Zone Failed";
                String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                        : "Operation failed. Please try again.";
                showErrorAlert(title, msg);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Edit Zone" button action (AC: 3).
     */
    @FXML
    private void handleEditZone() {
        Zone selectedZone = zonesViewModel.selectedZoneProperty().get();
        if (selectedZone == null)
            return;

        FXMLLoader loader = createZoneFXMLLoader();
        if (loader == null)
            return;

        ZoneFormController formController = loader.getController();
        formController.setMode(ZoneFormController.Mode.EDIT);
        formController.setZone(selectedZone);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Zone");
        dialog.getDialogPane().setContent((Parent) loader.getRoot());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (!formController.validate())
                event.consume();
        });

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
                Throwable ex = task.getException();
                LOGGER.error("Edit zone failed", ex);
                String title = (ex instanceof SecurityException) ? "Access Denied" : "Edit Zone Failed";
                String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                        : "Operation failed. Please try again.";
                showErrorAlert(title, msg);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Handles the "Delete Zone" button action (AC: 4).
     */
    @FXML
    private void handleDeleteZone() {
        Zone selectedZone = zonesViewModel.selectedZoneProperty().get();
        if (selectedZone == null)
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Zone");
        confirm.setHeaderText(null);
        String zoneName = selectedZone.getName() != null ? selectedZone.getName() : "(unnamed)";
        confirm.setContentText("Delete zone '" + zoneName + "'? All seats in this zone will be removed.");

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
                Throwable ex = task.getException();
                LOGGER.error("Delete zone failed", ex);
                String title = (ex instanceof SecurityException) ? "Access Denied" : "Delete Zone Failed";
                String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                        : "Operation failed. Please try again.";
                showErrorAlert(title, msg);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    /**
     * Loads {@code ZoneFormView.fxml} and returns the initialised
     * {@link FXMLLoader} so callers can retrieve both the root node and
     * the {@link ZoneFormController}.
     *
     * @return the loaded {@link FXMLLoader}, or {@code null} on I/O error
     */
    private FXMLLoader createZoneFXMLLoader() {
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

    // -------------------------------------------------------------------------
    // Seat Management handlers
    // -------------------------------------------------------------------------

    private void loadSeatsAsync(int zoneId) {
        Task<List<Seat>> task = new Task<>() {
            @Override
            protected List<Seat> call() throws Exception {
                return seatService.getSeatsByZone(zoneId);
            }
        };
        task.setOnSucceeded(e -> {
            List<Seat> seats = new ArrayList<>(task.getValue());
            seats.sort(Comparator.comparing(Seat::getRowNumber, AdminDashboardController::numericStringCompare)
                    .thenComparing(Seat::getSeatNumber, AdminDashboardController::numericStringCompare));
            seatsViewModel.setSeats(seats);
            // Defer to next pulse: setSeats() may fire the selection-change listener
            // synchronously, which calls renderSeatMap() and may resize the canvas.
            // Calling renderSeatMap() again in the same pulse while the Prism RTTexture
            // is invalidated causes an NPE in NGCanvas.validate().
            Platform.runLater(this::renderSeatMap);
            // AC4: count summary, with optional pending note from toggle handlers
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
        task.setOnFailed(e -> {
            LOGGER.error("Failed to load seats for zone {}", zoneId, task.getException());
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void renderSeatMap() {
        // Guard: skip when the Seating tab is not selected — the prism RTTexture
        // for the canvas is only allocated after the tab has been shown at least once.
        // Drawing before that causes NGCanvas.initCanvas NPE.
        if (mainTabPane.getSelectionModel().getSelectedItem() != seatingTab)
            return;
        double canvasWidth = seatMapCanvas.getWidth();
        double canvasHeight = seatMapCanvas.getHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0)
            return;

        List<Seat> seats = seatsViewModel.seatsProperty();
        seatCells.clear();

        if (seats.isEmpty()) {
            // Hide scrollbars — no content to scroll.
            seatMapScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            seatMapScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            GraphicsContext gc = seatMapCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvasWidth, canvasHeight);
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font(13));
            if (seatsZoneSelector.getSelectionModel().getSelectedItem() == null) {
                gc.fillText("Select a zone above", PADDING, 30);
            } else {
                gc.fillText("No seats configured for this zone", PADDING, 30);
            }
            return;
        }

        // Show scrollbars when seats are present.
        seatMapScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        seatMapScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Group by rowNumber preserving DB order (already sorted row_number ASC,
        // seat_number ASC)
        LinkedHashMap<String, List<Seat>> byRow = new LinkedHashMap<>();
        for (Seat s : seats) {
            byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
        }

        // Grow the canvas to fit all seats — resize both dimensions at once to keep
        // the number of deferred pulses to one. After any resize, double-defer so the
        // Prism RTTexture has two frames to be reallocated before we draw into it.
        int maxRowLength = byRow.values().stream().mapToInt(List::size).max().orElse(0);
        double requiredWidth  = Math.min(PADDING + maxRowLength * (CELL_SIZE + CELL_GAP) + PADDING, 8192.0);
        double requiredHeight = Math.min(PADDING + byRow.size()   * (CELL_SIZE + ROW_GAP)  + PADDING, 8192.0);
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
            // Same visibility-toggle pattern as layoutCanvas: hide during resize so Prism
            // skips the canvas (no initCanvas call, no RTTexture NPE), then show and draw.
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
                Color fill = switch (seat.getStatus()) {
                    case AVAILABLE -> Color.web("#4CAF50");
                    case SOLD -> Color.web("#F44336");
                    case DISABLED -> Color.web("#9E9E9E");
                    default -> Color.web("#9E9E9E");
                };
                gc.setFill(fill);
                gc.fillRoundRect(xOffset, yOffset, CELL_SIZE, CELL_SIZE, 4, 4);
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font(10));
                String label = seat.getSeatNumber() != null ? seat.getSeatNumber() : "";
                textMeasurer.setText(label);
                double textWidth = textMeasurer.getBoundsInLocal().getWidth();
                double textX = xOffset + (CELL_SIZE - textWidth) / 2.0;
                double textY = yOffset + CELL_SIZE / 2.0 + 4;
                gc.fillText(label, textX, textY);
                seatCells.add(new SeatCell(seat, xOffset, yOffset));
                if (seatsTable.getSelectionModel().getSelectedItems().contains(seat)) {
                    gc.setStroke(Color.WHITE);
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

    // ── Layout View event handlers ───────────────────────────────────

    private void handleLayoutScroll(javafx.scene.input.ScrollEvent e) {
        if (e.getDeltaY() == 0) return;
        double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
        double newZoom = Math.max(0.5, Math.min(3.0, layoutZoom * delta));
        if (newZoom == layoutZoom) return;

        // Zoom toward the mouse cursor position
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
        // Convert screen coords to world coords (invert zoom+pan)
        double worldX = (e.getX() - layoutPanX) / layoutZoom;
        double worldY = (e.getY() - layoutPanY) / layoutZoom;

        for (LayoutSeatCell cell : layoutSeatCells) {
            if (worldX >= cell.worldX() && worldX <= cell.worldX() + LAYOUT_CELL_SIZE
                    && worldY >= cell.worldY() && worldY <= cell.worldY() + LAYOUT_CELL_SIZE) {
                String zoneName  = (cell.zone() != null) ? cell.zone().getName() : "?";
                String price     = (cell.zone() != null && cell.zone().getPrice() != null)
                        ? cell.zone().getPrice().toPlainString() : "?";
                String rowNum    = cell.seat().getRowNumber() != null ? cell.seat().getRowNumber() : "?";
                String seatNum   = cell.seat().getSeatNumber() != null ? cell.seat().getSeatNumber() : "?";
                layoutHoverLabel.setText(
                        "Zone: " + zoneName
                        + ", Row: " + rowNum
                        + ", Seat: " + seatNum
                        + ", Price: \u20AC" + price
                        + ", Status: " + cell.seat().getStatus());
                return;
            }
        }
        layoutHoverLabel.setText("");
    }

    private void handleCanvasMousePressed(MouseEvent event) {
        SeatCell cell = findCellAt(event.getX(), event.getY());
        if (cell == null)
            return;
        int idx = seatsViewModel.seatsProperty().indexOf(cell.seat());
        if (idx < 0)
            return;
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
        if (cell == null)
            return;
        int idx = seatsViewModel.seatsProperty().indexOf(cell.seat());
        if (idx >= 0 && !seatsTable.getSelectionModel().isSelected(idx)) {
            seatsTable.getSelectionModel().select(idx);
        }
    }

    @FXML
    private void handleGenerateSeats() {
        Zone selectedZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
        if (selectedZone == null)
            return;

        String row = seatsRowField.getText();
        if (row == null || row.isBlank()) {
            showGenerateError("Error: Row must not be blank");
            return;
        }

        int from;
        int to;
        try {
            from = Integer.parseInt(seatsFromField.getText());
            to = Integer.parseInt(seatsToField.getText());
        } catch (NumberFormatException e) {
            showGenerateError("Error: From and To must be valid integers");
            return;
        }

        if (from < 1) {
            showGenerateError("Error: From must be >= 1");
            return;
        }
        if (to < from) {
            showGenerateError("Error: To must be >= From");
            return;
        }
        if (to - from + 1 > 1000) {
            showGenerateError("Error: Range must not exceed 1000 seats");
            return;
        }

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
            if (currentEvent != null)
                loadZonesAsync(currentEvent.getEventId());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            LOGGER.error("Generate seats failed", ex);
            String msg;
            if (isDuplicateError(ex)) {
                msg = "Error: Duplicate seats detected. No seats were created.";
            } else if (ex != null && ex.getMessage() != null && ex.getMessage().contains("value too long")) {
                msg = "Error: The row name or seat number is too long (max 10 characters). Please shorten it and try again.";
            } else {
                msg = "Error generating seats. Please try again.";
            }
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Generate Seats Failed");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
            // AC6: refresh table so unchanged seats remain visible after rollback
            Zone z = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (z != null)
                loadSeatsAsync(z.getZoneId());
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleMarkUnavailable() {
        // Patch 5: re-entrancy guard
        if (toggleInProgress) return;

        List<Seat> selected = new ArrayList<>(seatsTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        List<Seat> eligible = selected.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .toList();
        long soldCount = selected.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();

        // Patch 2: accurate empty-eligible message
        if (eligible.isEmpty()) {
            seatsStatusLabel.setText(soldCount > 0
                    ? "SOLD seats skipped \u2014 no changes made"
                    : "No eligible seats in selection.");
            return;
        }

        List<Integer> seatIds = eligible.stream().map(Seat::getSeatId).toList();
        Zone selectedZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
        if (selectedZone == null) {
            seatsStatusLabel.setText("No zone selected.");
            return;
        }
        int zoneId = selectedZone.getZoneId();
        // Patch 7: null guard on capturedAdmin
        User capturedAdmin = currentAdminUser;
        if (capturedAdmin == null) {
            seatsStatusLabel.setText("Error: no active admin session.");
            return;
        }

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
            // Patch 6: stale zone guard
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            // Patches 3+4: set pending note; loadSeatsAsync will compose it with count summary
            pendingToggleNote = soldCount > 0
                    ? "Updated " + seatIds.size() + " seat(s). " + soldCount + " SOLD seat(s) skipped."
                    : "Updated " + seatIds.size() + " seat(s).";
            loadSeatsAsync(zoneId);
        });
        task.setOnFailed(e -> {
            toggleInProgress = false;
            LOGGER.error("Mark unavailable failed", task.getException());
            // Patch 6: stale zone guard
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            // Patches 3+4: error note persists via pending note mechanism
            pendingToggleNote = "Error updating seats. Please try again.";
            loadSeatsAsync(zoneId);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleMarkAvailable() {
        // Patch 5: re-entrancy guard
        if (toggleInProgress) return;

        List<Seat> selected = new ArrayList<>(seatsTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        List<Seat> eligible = selected.stream()
                .filter(s -> s.getStatus() == SeatStatus.DISABLED)
                .toList();
        long soldCount = selected.stream().filter(s -> s.getStatus() == SeatStatus.SOLD).count();

        // Patch 2: accurate empty-eligible message
        if (eligible.isEmpty()) {
            seatsStatusLabel.setText(soldCount > 0
                    ? "SOLD seats skipped \u2014 no changes made"
                    : "No eligible seats in selection.");
            return;
        }

        List<Integer> seatIds = eligible.stream().map(Seat::getSeatId).toList();
        Zone selectedZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
        if (selectedZone == null) {
            seatsStatusLabel.setText("No zone selected.");
            return;
        }
        int zoneId = selectedZone.getZoneId();
        // Patch 7: null guard on capturedAdmin
        User capturedAdmin = currentAdminUser;
        if (capturedAdmin == null) {
            seatsStatusLabel.setText("Error: no active admin session.");
            return;
        }

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
            // Patch 6: stale zone guard
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            // Patches 3+4: set pending note; loadSeatsAsync will compose it with count summary
            pendingToggleNote = soldCount > 0
                    ? "Updated " + seatIds.size() + " seat(s). " + soldCount + " SOLD seat(s) skipped."
                    : "Updated " + seatIds.size() + " seat(s).";
            loadSeatsAsync(zoneId);
        });
        task.setOnFailed(e -> {
            toggleInProgress = false;
            LOGGER.error("Mark available failed", task.getException());
            // Patch 6: stale zone guard
            Zone currentZone = seatsZoneSelector.getSelectionModel().getSelectedItem();
            if (currentZone == null || currentZone.getZoneId() != zoneId) return;
            // Patches 3+4: error note persists via pending note mechanism
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
        if (selected.isEmpty())
            return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Seats");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete " + selected.size() + " seat(s)? This cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            List<Integer> seatIds = new ArrayList<>();
            for (Seat s : selected) {
                seatIds.add(s.getSeatId());
            }
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
                if (zoneId > 0)
                    loadSeatsAsync(zoneId);
                Event currentEvent = zonesEventSelector.getSelectionModel().getSelectedItem();
                if (currentEvent != null)
                    loadZonesAsync(currentEvent.getEventId());
            });
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                LOGGER.error("Delete seats failed", ex);
                String msg = "Error deleting seats. Please try again.";
                Alert errAlert = new Alert(Alert.AlertType.ERROR);
                errAlert.setTitle("Delete Seats Failed");
                errAlert.setHeaderText(null);
                errAlert.setContentText(msg);
                errAlert.showAndWait();
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    private void showGenerateError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Generate Seats Failed");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateGenerateButtonState() {
        boolean zoneSelected = seatsZoneSelector.getSelectionModel().getSelectedItem() != null;
        boolean rowFilled = seatsRowField.getText() != null && !seatsRowField.getText().isBlank();
        boolean fromValid = isPositiveInt(seatsFromField.getText());
        boolean toValid = isPositiveInt(seatsToField.getText());
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

    private boolean isDuplicateError(Throwable ex) {
        if (ex instanceof java.sql.SQLException sqle) {
            return "23505".equals(sqle.getSQLState());
        }
        if (ex != null && ex.getCause() instanceof java.sql.SQLException sqle) {
            return "23505".equals(sqle.getSQLState());
        }
        String msg = ex != null ? ex.getMessage() : null;
        return msg != null && (msg.contains("duplicate") || msg.contains("unique constraint") || msg.contains("23505"));
    }

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

        java.io.File file = fileChooser.showSaveDialog(
                layoutCanvas.getScene().getWindow());
        if (file == null) return;

        // Snapshot seat/zone data before handing off to the background thread.
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
            alert.setContentText("Could not generate the PDF. Please check that the destination folder is writable and try again.");
            alert.showAndWait();
        });
        Thread t = new Thread(exportTask);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Generates a PDF seating chart using Apache PDFBox 3.0.1.
     * Each zone occupies a section with a bold header; seats are colored rectangles.
     */
    private void exportLayoutToPdf(java.io.File file, Event event,
            List<Seat> seats, Map<Integer, Zone> zoneMap) throws Exception {
        float pageW    = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth();
        float pageH    = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight();
        float margin   = 40f;
        float labelW   = 50f;   // width reserved for row label (fits up to 10 chars at 9pt)
        float cellSize = 22f;
        float cellGap  = 3f;
        float rowGap   = 6f;
        float zoneGap  = 14f;
        float lineH    = cellSize + rowGap;

        int seatsPerLine = Math.min(LAYOUT_MAX_SEATS_ROW, Math.max(1, (int) ((pageW - 2 * margin - labelW) / (cellSize + cellGap))));

        org.apache.pdfbox.pdmodel.font.PDType1Font pdfBold = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);
        org.apache.pdfbox.pdmodel.font.PDType1Font pdfNormal = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);

        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            // Start first page
            org.apache.pdfbox.pdmodel.PDPage curPage =
                    new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(curPage);
            org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, curPage);
            float yPos = pageH - margin;

            try {
                // suppress IDE warning — cs is reassigned on page breaks; each old stream is
                // explicitly closed before reassignment; finally closes the last open stream.
                // Title
                cs.beginText();
                cs.setFont(pdfBold, 14);
                cs.newLineAtOffset(margin, yPos);
                cs.showText(toPdfSafe("Seating Layout: " + event.getName()));
                cs.endText();
                yPos -= 28;

                // Group by zone (insertion order = query order)
                LinkedHashMap<Integer, List<Seat>> byZone = new LinkedHashMap<>();
                for (Seat s : seats) {
                    byZone.computeIfAbsent(s.getZoneId(), k -> new ArrayList<>()).add(s);
                }

                for (Map.Entry<Integer, List<Seat>> zoneEntry : byZone.entrySet()) {
                    Zone zone = zoneMap.get(zoneEntry.getKey());
                    String zoneName = (zone != null) ? toPdfSafe(zone.getName()) : "Zone " + zoneEntry.getKey();

                    // Need room for zone header + spacing + at least one seat row
                    if (yPos - (28 + lineH) < margin) {
                        cs.close();
                        curPage = new org.apache.pdfbox.pdmodel.PDPage(
                                org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                        doc.addPage(curPage);
                        cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, curPage);
                        yPos = pageH - margin;
                    }

                    // Zone header
                    cs.beginText();
                    cs.setFont(pdfBold, 12);
                    cs.setNonStrokingColor(0.13f, 0.13f, 0.13f);
                    cs.newLineAtOffset(margin, yPos);
                    cs.showText(zoneName);  // already toPdfSafe'd above
                    cs.endText();
                    yPos -= 28;

                    // Group by row (numeric sort)
                    java.util.TreeMap<String, List<Seat>> byRow =
                            new java.util.TreeMap<>(AdminDashboardController::numericStringCompare);
                    for (Seat s : zoneEntry.getValue()) {
                        byRow.computeIfAbsent(s.getRowNumber(), k -> new ArrayList<>()).add(s);
                    }

                    for (Map.Entry<String, List<Seat>> rowEntry : byRow.entrySet()) {
                        String rowLabel = rowEntry.getKey() != null ? toPdfSafe(rowEntry.getKey()) : "";
                        List<Seat> rowSeats = new ArrayList<>(rowEntry.getValue());
                        rowSeats.sort((a, b) -> numericStringCompare(a.getSeatNumber(), b.getSeatNumber()));

                        // Wrap into chunks of seatsPerLine
                        for (int chunk = 0; chunk < rowSeats.size(); chunk += seatsPerLine) {
                            List<Seat> line = rowSeats.subList(
                                    chunk, Math.min(chunk + seatsPerLine, rowSeats.size()));

                            // New page if not enough vertical space
                            if (yPos - lineH < margin) {
                                cs.close();
                                curPage = new org.apache.pdfbox.pdmodel.PDPage(
                                        org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                                doc.addPage(curPage);
                                cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, curPage);
                                yPos = pageH - margin;
                            }

                            // Row label on the first chunk only
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

                                String seatNum = seat.getSeatNumber() != null ? toPdfSafe(seat.getSeatNumber()) : "";
                                if (!seatNum.isEmpty()) {
                                    float textWidth = pdfNormal.getStringWidth(seatNum) / 1000f * 8;
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

    // ── PDF helper ───────────────────────────────────────────────────────────

    /**
     * Sanitizes a string for use with PDType1Font (WinAnsiEncoding / Helvetica).
     * Characters outside ISO-8859-1 range (0–255) are replaced with '?' to prevent
     * {@link IllegalArgumentException} from PDFBox when the event/zone name contains
     * characters like em-dash, curly quotes, or CJK glyphs.
     */
    private static String toPdfSafe(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c <= 0xFF ? c : '?');
        }
        return sb.toString();
    }
}
