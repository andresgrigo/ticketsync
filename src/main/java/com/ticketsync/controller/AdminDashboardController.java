package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.Event;
import com.ticketsync.model.User;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.EventService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.service.UserManagementService;
import com.ticketsync.viewmodel.EventManagementViewModel;
import com.ticketsync.viewmodel.UserManagementViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * FXML controller for the admin dashboard view ({@code AdminDashboardView.fxml}).
 *
 * <p>Provides a tabbed interface for managing users. The Users tab displays
 * a {@link TableView} backed by a {@link UserManagementViewModel}; CRUD
 * operations are performed through {@link UserManagementService} on background
 * daemon threads so the FX application thread is never blocked.
 *
 * <p>Role-based access control is enforced in {@link #initialize()}: if the
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

    @FXML private Label loggedInUserLabel;
    @FXML private Button logoutButton;
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> createdAtColumn;
    @FXML private Button createUserButton;
    @FXML private Button editUserButton;
    @FXML private Button deleteUserButton;
    @FXML private Label statusLabel;

    @FXML private TableView<Event> eventsTable;
    @FXML private TableColumn<Event, String> eventNameColumn;
    @FXML private TableColumn<Event, String> eventDateColumn;
    @FXML private TableColumn<Event, String> eventVenueColumn;
    @FXML private TableColumn<Event, String> eventDescColumn;
    @FXML private TableColumn<Event, String> eventStatusColumn;
    @FXML private Button createEventButton;
    @FXML private Button editEventButton;
    @FXML private Button deleteEventButton;
    @FXML private Button toggleActivateButton;
    @FXML private Label eventsStatusLabel;

    /**
     * Initialises the controller after FXML injection.
     *
     * <ol>
     *   <li>Reads the current session from {@link SessionContext}.</li>
     *   <li>Enforces ADMIN role (AC: 12).</li>
     *   <li>Configures table columns and button bindings.</li>
     *   <li>Loads users asynchronously.</li>
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
            LOGGER.error("AdminDashboardController.initialize() — user '{}' has role '{}', not ADMIN; redirecting to login",
                    opt.get().getUsername(), opt.get().getRole());
            navigateToLogin();
            return;
        }

        currentAdminUser = opt.get();
        loggedInUserLabel.setText("Logged in as: " + currentAdminUser.getUsername());

        viewModel = new UserManagementViewModel();

        // Configure TableView columns
        usernameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getUsername()));
        roleColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRole()));
        createdAtColumn.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getCreatedAt();
            String formatted = (dt != null) ? dt.format(DT_FMT) : "—";
            return new SimpleStringProperty(formatted);
        });

        usersTable.setItems(viewModel.usersProperty());

        // Bind selection to ViewModel
        viewModel.selectedUserProperty().bind(usersTable.getSelectionModel().selectedItemProperty());

        // Bind edit/delete buttons: disabled when no selection OR selected user is self (AC: 10, 11)
        BooleanBinding noSelectionOrSelf = Bindings.createBooleanBinding(
                () -> {
                    User sel = usersTable.getSelectionModel().getSelectedItem();
                    if (sel == null) return true;
                    return sel.getUserId() == currentAdminUser.getUserId();
                },
                usersTable.getSelectionModel().selectedItemProperty()
        );
        editUserButton.disableProperty().bind(noSelectionOrSelf);
        deleteUserButton.disableProperty().bind(noSelectionOrSelf);

        loadUsersAsync();

        // ---- Event Management setup ----
        eventsViewModel = new EventManagementViewModel();

        eventNameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName() != null ? data.getValue().getName() : ""));

        eventDateColumn.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getEventDate();
            return new SimpleStringProperty(dt != null ? dt.format(DT_FMT) : "\u2014");
        });

        eventVenueColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getVenue() != null
                        ? data.getValue().getVenue() : "\u2014"));

        eventDescColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDescription() != null
                        ? data.getValue().getDescription() : ""));

        eventStatusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isActive() ? "Active" : "Inactive"));
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

        BooleanBinding noEventSelection =
                eventsTable.getSelectionModel().selectedItemProperty().isNull();
        editEventButton.disableProperty().bind(noEventSelection);
        deleteEventButton.disableProperty().bind(noEventSelection);
        toggleActivateButton.disableProperty().bind(noEventSelection);

        toggleActivateButton.textProperty().bind(
                Bindings.createStringBinding(
                        () -> {
                            Event sel = eventsTable.getSelectionModel().getSelectedItem();
                            if (sel == null) return "Activate";
                            return sel.isActive() ? "Deactivate" : "Activate";
                        },
                        eventsTable.getSelectionModel().selectedItemProperty()
                )
        );

        loadEventsAsync();
    }

    /**
     * Loads all users from the database on a background thread and populates
     * the {@link UserManagementViewModel} on the FX thread when complete.
     *
     * <p>Sets the status label to "Loading…" while the operation is in
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
     * <p>Opens {@code UserFormView.fxml} in a dialog. An event filter on the
     * OK button calls {@link UserFormController#validate()} before allowing
     * the dialog to close, so invalid input is surfaced without dismissing
     * the dialog.
     */
    @FXML
    private void handleCreateUser() {
        FXMLLoader loader = createFXMLLoader();
        if (loader == null) return;

        UserFormController formController = loader.getController();
        formController.setMode(UserFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = buildFormDialog("Create User", (Parent) loader.getRoot(), formController);

        // Second event filter: inline username uniqueness check (AC: 5).
        // Fires only after the primary validate() filter passes (event not yet consumed).
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
     * <p>Pre-populates the form with the selected user's data (username
     * read-only, password fields hidden). On confirmation the user's role
     * is updated via {@link UserManagementService#updateUserRole}.
     */
    @FXML
    private void handleEditUser() {
        User selectedUser = viewModel.selectedUserProperty().get();
        if (selectedUser == null) return;

        FXMLLoader loader = createFXMLLoader();
        if (loader == null) return;

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
     * <p>Shows a confirmation alert before permanently deleting the selected
     * user from the database.
     */
    @FXML
    private void handleDeleteUser() {
        User selectedUser = viewModel.selectedUserProperty().get();
        if (selectedUser == null) return;

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
     * <p>Clears the current session and navigates to the login screen.
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
     * <p>Sets the status label to "Loading…" during the operation and clears
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
     * <p>Opens {@code EventFormView.fxml} in a dialog. An event filter on
     * the OK button calls {@link EventFormController#validate()} before
     * allowing the dialog to close. On confirmation the event is created via
     * {@link EventService#createEvent(Event)} on a background thread.
     */
    @FXML
    private void handleCreateEvent() {
        FXMLLoader loader = createEventFXMLLoader();
        if (loader == null) return;

        EventFormController formController = loader.getController();
        formController.setMode(EventFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Event");
        dialog.getDialogPane().setContent((Parent) loader.getRoot());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (!formController.validate()) event.consume();
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
                String msg   = (ex instanceof SecurityException) ? "ADMIN role is required." : "Operation failed. Please try again.";
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
     * <p>Pre-populates the event form with the selected event's data.
     * On confirmation the event is updated via
     * {@link EventService#updateEvent(Event)}, preserving identity fields.
     */
    @FXML
    private void handleEditEvent() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null) return;

        FXMLLoader loader = createEventFXMLLoader();
        if (loader == null) return;

        EventFormController formController = loader.getController();
        formController.setMode(EventFormController.Mode.EDIT);
        formController.setEvent(selectedEvent);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Event");
        dialog.getDialogPane().setContent((Parent) loader.getRoot());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION, event -> {
            if (!formController.validate()) event.consume();
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
                String msg   = (ex instanceof SecurityException) ? "ADMIN role is required." : "Operation failed. Please try again.";
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
     * <p>Shows a confirmation alert before permanently deleting the selected
     * event and all associated seating data.
     */
    @FXML
    private void handleDeleteEvent() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null) return;

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
                String msg   = (ex instanceof SecurityException) ? "ADMIN role is required." : "Operation failed. Please try again.";
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
     * <p>Calls {@link EventService#activateEvent(int)} or
     * {@link EventService#deactivateEvent(int)} depending on the selected
     * event's current active state, then refreshes the table.
     */
    @FXML
    private void handleToggleActivate() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null) return;

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
            String msg   = (ex instanceof SecurityException) ? "ADMIN role is required." : "Operation failed. Please try again.";
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
     * <p>Called when RBAC enforcement in {@link #initialize()} fails so that
     * the admin dashboard is never shown to non-admin users (AC: 12).
     */
    private void navigateToLogin() {
        try {
            App.setRoot("LoginView");
        } catch (IOException ex) {
            LOGGER.error("Failed to navigate to LoginView", ex);
        }
    }
}
