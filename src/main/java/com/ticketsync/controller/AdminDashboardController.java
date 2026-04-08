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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
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
 * and navigates back to the login screen (AC: 12, NFR-SEC02).
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
    private Canvas seatMapCanvas;
    @FXML
    private ScrollPane seatMapScrollPane;
    @FXML
    private Label seatMapHoverLabel;

    private SeatManagementViewModel seatsViewModel;
    private final SeatService seatService = new SeatService();

    private static final int CELL_SIZE = 32;
    private static final int CELL_GAP = 4;
    private static final int ROW_GAP = 8;
    private static final int PADDING = 10;

    private record SeatCell(Seat seat, double x, double y) {
    }

    private List<SeatCell> seatCells = new ArrayList<>();

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

        // Load seats when zone is selected in seatsZoneSelector
        seatsZoneSelector.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldZone, newZone) -> {
                    if (newZone != null) {
                        loadSeatsAsync(newZone.getZoneId());
                    } else {
                        seatsViewModel.setSeats(Collections.emptyList());
                        renderSeatMap();
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
                .addListener((javafx.collections.ListChangeListener<Seat>) change -> renderSeatMap());
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
        });
        task.setOnFailed(e -> {
            LOGGER.error("Failed to load seats for zone {}", zoneId, task.getException());
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void renderSeatMap() {
        // Guard: canvas has no backing texture until it has been laid out with positive
        // dimensions.
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

        // Grow the canvas to fit all seats — one dimension per pulse to avoid
        // double-invalidation of the Prism RTTexture which causes an NPE.
        int maxRowLength = byRow.values().stream().mapToInt(List::size).max().orElse(0);
        double requiredWidth  = PADDING + maxRowLength * (CELL_SIZE + CELL_GAP) + PADDING;
        double requiredHeight = PADDING + byRow.size() * (CELL_SIZE + ROW_GAP) + PADDING;
        if (canvasWidth < requiredWidth) {
            seatMapCanvas.setWidth(requiredWidth);
            Platform.runLater(this::renderSeatMap);
            return;
        }
        if (canvasHeight < requiredHeight) {
            seatMapCanvas.setHeight(requiredHeight);
            Platform.runLater(this::renderSeatMap);
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
}
