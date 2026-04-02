package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.User;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.service.UserManagementService;
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
    private UserManagementViewModel viewModel;
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
