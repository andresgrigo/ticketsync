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
import java.net.ConnectException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;

/**
 * Controlador FXML para la pantalla de inicio de sesión ({@code LoginView.fxml}).
 *
 * <p>Enlaza los controles de la UI de JavaFX a un {@link LoginViewModel} y delega
 * la autenticación a {@link AuthenticationService}. La llamada real a
 * {@code authService.login()} se ejecuta en un hilo {@link Task} en segundo plano
 * para que el hilo de la aplicación FX nunca sea bloqueado.
 *
 * <p>Tras una autenticación exitosa, el controlador navega a la vista del panel de
 * administración o a la vista del POS del vendedor, según el rol del usuario autenticado.
 */
public class LoginController {

    /** Crea una nueva instancia de {@code LoginController} (invocada por FXMLLoader mediante reflexión). */
    public LoginController() {
    }

    private static final Logger LOGGER= LogManager.getLogger(LoginController.class);

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
     * Inicializa el controlador después de que la raíz FXML ha sido completamente procesada.
     *
     * <p>Instancia el {@link LoginViewModel} y establece
     * enlaces bidireccionales entre los controles de UI y las propiedades del ViewModel.
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
     * Maneja la acción de inicio de sesión activada por el botón de inicio de sesión o la tecla Enter
     * en el campo de contraseña.
     *
     * <p>Valida que se haya ingresado un nombre de usuario, luego envía una
     * {@link Task} de autenticación en un nuevo hilo demonio. Los controles de UI se
     * deshabilitan durante la tarea para prevenir el doble envío.
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
            Throwable ex = loginTask.getException();
            LOGGER.error("Login task failed", ex);
            if (isDatabaseUnavailable(ex)) {
                viewModel.errorMessageProperty().set("Cannot connect to the database. Please try again later.");
            } else {
                viewModel.errorMessageProperty().set("A system error occurred. Please try again.");
            }
            viewModel.loginInProgressProperty().set(false);
        });

        Thread thread = new Thread(loginTask);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Navega a la vista apropiada para el rol del usuario autenticado.
     *
     * <p>Este método se ejecuta en el hilo de la aplicación FX (invocado desde
     * {@code Task.setOnSucceeded}). Establece el usuario actual en
     * {@link SessionContext} en el hilo FX antes de cargar el FXML destino
     * para que controladores como {@code AdminDashboardController}
     * puedan leer la sesión mediante {@code SessionContext.getCurrentUser()}
     * durante su método de ciclo de vida {@code initialize()}.
     *
     * @param user el {@link User} autenticado; no debe ser {@code null}
     */
    private void navigateToRoleView(User user) {
        String role = user.getRole();
        if (role == null) {
            LOGGER.error("Null role for userId={} — cannot navigate", user.getUserId());
            viewModel.errorMessageProperty().set("Account configuration error. Please contact your administrator.");
            return;
        }
        try {
            // Popula el ThreadLocal del hilo FX para que AdminDashboardController.initialize()
            // pueda leer la sesión. AuthenticationService.login() se ejecuta en un hilo Task
            // en segundo plano y establece el usuario allí, pero este método se ejecuta en el hilo FX.
            try {
                SessionContext.setCurrentUser(user);
            } catch (IllegalArgumentException ex) {
                // user está garantizado como no nulo aquí; esta rama es inalcanzable en la práctica
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

    private static boolean isDatabaseUnavailable(Throwable t) {
        while (t != null) {
            if (t instanceof SQLTransientConnectionException || t instanceof ConnectException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
