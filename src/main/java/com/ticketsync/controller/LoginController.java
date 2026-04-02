package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.User;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.viewmodel.LoginViewModel;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Optional;

/**
 * FXML controller for the login screen ({@code LoginView.fxml}).
 *
 * <p>Binds JavaFX UI controls to a {@link LoginViewModel} and delegates
 * authentication to {@link AuthenticationService}. The actual
 * {@code authService.login()} call is executed on a background
 * {@link Task} thread so the FX application thread is never blocked.
 *
 * <p>On successful authentication the controller navigates to either the
 * admin dashboard view or the vendor POS view based on the authenticated
 * user's role.
 */
public class LoginController {

    private static final Logger LOGGER = LogManager.getLogger(LoginController.class);

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    private final AuthenticationService authService = new AuthenticationService();
    private LoginViewModel viewModel;

    /**
     * Initialises the controller after the FXML root has been fully
     * processed.
     *
     * <p>Instantiates the {@link LoginViewModel} and establishes
     * bidirectional bindings between UI controls and ViewModel properties.
     */
    @FXML
    public void initialize() {
        viewModel = new LoginViewModel();

        usernameField.textProperty().bindBidirectional(viewModel.usernameProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());

        loginButton.disableProperty().bind(viewModel.loginInProgressProperty());
        usernameField.disableProperty().bind(viewModel.loginInProgressProperty());
        passwordField.disableProperty().bind(viewModel.loginInProgressProperty());

        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
    }

    /**
     * Handles the login action triggered by the login button or Enter key
     * on the password field.
     *
     * <p>Validates that a username has been entered, then submits an
     * authentication {@link Task} on a new daemon thread. UI controls are
     * disabled for the duration of the task to prevent double-submission
     * (AC 7, NFR-U04).
     */
    @FXML
    private void handleLogin() {
        if (viewModel.loginInProgressProperty().get()) {
            return;
        }

        viewModel.resetError();

        String username = viewModel.usernameProperty().get();
        String password = viewModel.passwordProperty().get();

        if (username == null || username.isBlank()) {
            viewModel.errorMessageProperty().set("Please enter your username");
            return;
        }

        if (password == null || password.isBlank()) {
            viewModel.errorMessageProperty().set("Please enter your password");
            return;
        }

        Task<Optional<User>> loginTask = new Task<>() {
            @Override
            protected Optional<User> call() throws Exception {
                return authService.login(username, password);
            }
        };

        viewModel.loginInProgressProperty().set(true);

        loginTask.setOnSucceeded(e -> {
            Optional<User> result = loginTask.getValue();
            try {
                if (result.isPresent()) {
                    navigateToRoleView(result.get());
                } else {
                    viewModel.errorMessageProperty().set("Invalid username or password");
                    passwordField.clear();
                    usernameField.requestFocus();
                }
            } finally {
                viewModel.loginInProgressProperty().set(false);
            }
        });

        loginTask.setOnFailed(e -> {
            LOGGER.error("Login task failed", loginTask.getException());
            viewModel.errorMessageProperty().set("A system error occurred. Please try again.");
            viewModel.loginInProgressProperty().set(false);
        });

        Thread thread = new Thread(loginTask);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Navigates to the appropriate view for the authenticated user's role.
     *
     * <p>This method runs on the FX application thread (invoked from
     * {@code Task.setOnSucceeded}). It sets the current user in
     * {@link SessionContext} on the FX thread before loading the target
     * FXML so that controllers such as {@code AdminDashboardController}
     * can read the session via {@code SessionContext.getCurrentUser()}
     * during their {@code initialize()} lifecycle method.
     *
     * @param user the authenticated {@link User}; must not be {@code null}
     */
    private void navigateToRoleView(User user) {
        String role = user.getRole();
        if (role == null) {
            LOGGER.error("Null role for userId={} — cannot navigate", user.getUserId());
            viewModel.errorMessageProperty().set("Account configuration error. Please contact your administrator.");
            return;
        }
        try {
            // Populate the FX thread's ThreadLocal so AdminDashboardController.initialize()
            // can read the session. AuthenticationService.login() runs on a background Task
            // thread and sets the user there, but this method runs on the FX thread.
            try {
                SessionContext.setCurrentUser(user);
            } catch (IllegalArgumentException ex) {
                // user is guaranteed non-null here; this branch is unreachable in practice
                LOGGER.error("Unexpected null user passed to navigateToRoleView", ex);
                viewModel.errorMessageProperty().set("A system error occurred. Please try again.");
                return;
            }

            if ("ADMIN".equalsIgnoreCase(role)) {
                App.setRoot("AdminDashboardView");
            } else if ("VENDOR".equalsIgnoreCase(role)) {
                App.setRoot("PosView");
            } else {
                LOGGER.error("Unrecognised role '{}' for userId={}", role, user.getUserId());
                viewModel.errorMessageProperty().set("Unknown account role. Please contact your administrator.");
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to navigate to role view for role: {}", role, ex);
            viewModel.errorMessageProperty().set("A system error occurred. Please try again.");
        }
    }
}
