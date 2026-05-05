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
 * Shell controller for AdminDashboardView.fxml.
 * Enforces RBAC, bootstraps sub-controllers, wires the tab-change listener.
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

    /** Creates a new AdminDashboardController; instantiated by FXMLLoader. */
    public AdminDashboardController() { }

    /**
     * FXML lifecycle method — invoked by FXMLLoader after all @FXML fields are injected.
     * Validates the current session has ADMIN role, then bootstraps sub-controllers.
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
