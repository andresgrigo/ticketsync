package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.User;
import com.ticketsync.service.UserManagementService;
import com.ticketsync.util.DialogThemeHelper;
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
 * Controlador de pestaña de administrador para la sección de gestión de usuarios del Panel de Administración.
 *
 * <p>Muestra la lista completa de usuarios en un {@link javafx.scene.control.TableView}, y
 * conecta las acciones de la barra de herramientas de creación, edición y eliminación a las operaciones
 * de {@link UserManagementService} mediante {@link UserManagementViewModel}.
 */
public class UsersTabController {

    private static final Logger LOGGER = LogManager.getLogger(UsersTabController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserManagementService userService = new UserManagementService();
    private UserManagementViewModel viewModel;
    private User currentAdminUser;

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> createdAtColumn;
    @FXML private Button createUserButton;
    @FXML private Button editUserButton;
    @FXML private Button deleteUserButton;
    @FXML private Label statusLabel;

    /** Crea un nuevo UsersTabController; instanciado por FXMLLoader. */
    public UsersTabController() { }

    /**
     * Método del ciclo de vida FXML; configura las columnas de la tabla y las enlaza al view model.
     * Invocado por FXMLLoader después de que todos los campos {@code @FXML} son inyectados.
     */
    @FXML
    public void initialize() {
        viewModel = new UserManagementViewModel();

        usernameColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getUsername()));
        roleColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getRole()));
        createdAtColumn.setCellValueFactory(data -> {
            LocalDateTime dt = data.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.format(DT_FMT) : "\u2014");
        });

        usersTable.setItems(viewModel.usersProperty());
        viewModel.selectedUserProperty().bind(
                usersTable.getSelectionModel().selectedItemProperty());
    }

    /**
     * Establece el usuario administrador autenticado y carga la lista inicial de usuarios.
     * Llamado por el controlador shell padre una vez que la identidad del administrador es conocida.
     *
     * @param admin el usuario administrador actualmente autenticado; debe tener el rol ADMIN
     */
    public void setAdminUser(User admin) {
        this.currentAdminUser = admin;

        BooleanBinding noSelectionOrSelf = Bindings.createBooleanBinding(
                () -> {
                    User sel = usersTable.getSelectionModel().getSelectedItem();
                    if (sel == null) return true;
                    return sel.getUserId() == currentAdminUser.getUserId();
                },
                usersTable.getSelectionModel().selectedItemProperty());
        editUserButton.disableProperty().bind(noSelectionOrSelf);
        deleteUserButton.disableProperty().bind(noSelectionOrSelf);

        loadUsersAsync();
    }

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

    @FXML
    private void handleCreateUser() {
        FXMLLoader loader = createUserFormLoader();
        if (loader == null) return;

        UserFormController formController = loader.getController();
        formController.setMode(UserFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = buildFormDialog("Create User", loader.getRoot(), formController);

        Button createOkBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        createOkBtn.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (userService.usernameExists(formController.getUsername())) {
                    formController.showExternalError("Username is already taken");
                    event.consume();
                }
            } catch (SQLException ex) {
                LOGGER.error("Username uniqueness check failed", ex);
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
                    return userService.createUser(username, password, role,
                            currentAdminUser.getUsername());
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

    @FXML
    private void handleEditUser() {
        User selectedUser = viewModel.selectedUserProperty().get();
        if (selectedUser == null) return;

        FXMLLoader loader = createUserFormLoader();
        if (loader == null) return;

        UserFormController formController = loader.getController();
        formController.setMode(UserFormController.Mode.EDIT);
        formController.setUser(selectedUser);

        Dialog<ButtonType> dialog = buildFormDialog("Edit User", loader.getRoot(), formController);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String newRole = formController.getSelectedRole();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    userService.updateUserRole(selectedUser, newRole,
                            currentAdminUser.getUsername());
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

    @FXML
    private void handleDeleteUser() {
        User selectedUser = viewModel.selectedUserProperty().get();
        if (selectedUser == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogThemeHelper.apply(confirm);
        confirm.setTitle("Delete User");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Are you sure you want to delete user '" + selectedUser.getUsername() + "'?");
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

    // ── Ayudantes ───────────────────────────────────────────────────────────────

    private FXMLLoader createUserFormLoader() {
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

    private Dialog<ButtonType> buildFormDialog(String title, Parent formContent,
            UserFormController formController) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(formContent);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogThemeHelper.apply(dialog);
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION,
                event -> { if (!formController.validate()) event.consume(); });
        return dialog;
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
}
