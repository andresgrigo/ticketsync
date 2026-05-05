package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.User;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.SessionContext;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Optional;

/**
 * Controlador shell para AdminDashboardView.fxml.
 * Aplica RBAC, inicializa los sub-controladores y conecta el listener de cambio de pestaña.
 */
public class AdminDashboardController {

    private static final Logger LOGGER = LogManager.getLogger(AdminDashboardController.class);

    @FXML private Label loggedInUserLabel;
    @FXML private TabPane mainTabPane;
    @FXML private Tab seatingTab;
    @FXML private Tab layoutViewTab;
    @FXML private Tab auditLogTab;

    @FXML private UsersTabController usersTabContentController;
    @FXML private EventsTabController eventsTabContentController;
    @FXML private SeatingTabController seatingTabContentController;
    @FXML private LayoutViewTabController layoutViewTabContentController;
    @FXML private AuditLogTabController auditLogTabContentController;

    /** Crea un nuevo AdminDashboardController; instanciado por FXMLLoader. */
    public AdminDashboardController() { }

    /**
     * Método de ciclo de vida FXML — invocado por FXMLLoader después de que todos los campos @FXML son inyectados.
     * Valida que la sesión actual tiene rol ADMIN, luego inicializa los sub-controladores.
     */
    @FXML
    public void initialize() {
        Optional<User> opt = SessionContext.getCurrentUser();
        if (opt.isEmpty()) {
            LOGGER.error("No session user — redirecting to login");
            navigateToLogin();
            return;
        }
        if (!"ADMIN".equalsIgnoreCase(opt.get().getRole())) {
            LOGGER.error("User '{}' has role '{}', not ADMIN — redirecting to login",
                    opt.get().getUsername(), opt.get().getRole());
            navigateToLogin();
            return;
        }

        User admin = opt.get();
        loggedInUserLabel.setText("Logged in as: " + admin.getUsername());

        usersTabContentController.setAdminUser(admin);
        eventsTabContentController.setAdminUser(admin);
        seatingTabContentController.setAdminUser(admin);
        layoutViewTabContentController.setAdminUser(admin);
        auditLogTabContentController.setAdminUser(admin);

        seatingTabContentController.setTabActiveCheck(
                () -> mainTabPane.getSelectionModel().getSelectedItem() == seatingTab);
        layoutViewTabContentController.setTabActiveCheck(
                () -> mainTabPane.getSelectionModel().getSelectedItem() == layoutViewTab);
        auditLogTabContentController.setTabActiveCheck(
                () -> mainTabPane.getSelectionModel().getSelectedItem() == auditLogTab);

        mainTabPane.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> {
                    if (newTab == seatingTab) {
                        seatingTabContentController.onTabActivated();
                    } else if (newTab == layoutViewTab) {
                        layoutViewTabContentController.onTabActivated();
                    } else if (newTab == auditLogTab) {
                        auditLogTabContentController.onTabActivated();
                    }
                });
    }

    @FXML
    private void handleLogout() {
        try {
            new AuthenticationService().logout();
            App.setRoot("LoginView");
        } catch (IOException ex) {
            LOGGER.error("Failed to navigate to LoginView on logout", ex);
        }
    }

    private void navigateToLogin() {
        try {
            App.setRoot("LoginView");
        } catch (IOException ex) {
            LOGGER.error("Failed to navigate to LoginView", ex);
        }
    }
}
