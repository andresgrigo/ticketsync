package com.ticketsync.controller;

import com.ticketsync.model.AuditLog;
import com.ticketsync.model.User;
import com.ticketsync.service.AuditService;
import com.ticketsync.service.SessionContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Read-only admin reporting surface backed by the {@code audit_log} table.
 */
public class AuditLogTabController {

    enum TimeWindow {
        LAST_24_HOURS("Last 24 Hours", 1),
        LAST_7_DAYS("Last 7 Days", 7),
        LAST_30_DAYS("Last 30 Days", 30);

        private final String label;
        private final int days;

        TimeWindow(String label, int days) {
            this.label = label;
            this.days = days;
        }

        LocalDateTime startFrom(LocalDateTime endTime) {
            return endTime.minusDays(days);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger(AuditLogTabController.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ALL_ACTIONS = "All Actions";
    private static final int DEFAULT_LIMIT = 200;

    private final AuditService auditService;
    private User currentAdminUser;
    private Supplier<Boolean> isTabActive = () -> true;

    @FXML private ComboBox<TimeWindow> timeWindowCombo;
    @FXML private ComboBox<String> actionFilterCombo;
    @FXML private TextField usernameFilterField;
    @FXML private Button refreshButton;
    @FXML private Label auditStatusLabel;
    @FXML private TableView<AuditLog> auditTable;
    @FXML private TableColumn<AuditLog, String> timestampColumn;
    @FXML private TableColumn<AuditLog, String> usernameColumn;
    @FXML private TableColumn<AuditLog, String> actionColumn;
    @FXML private TableColumn<AuditLog, String> entityColumn;
    @FXML private TableColumn<AuditLog, String> detailsColumn;

    /**
     * Creates a new AuditLogTabController using the production {@link AuditService}.
     * Instantiated by FXMLLoader via reflection.
     */
    public AuditLogTabController() {
        this(new AuditService());
    }

    AuditLogTabController(AuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    /**
     * FXML lifecycle method; binds table columns and loads initial audit data.
     * Invoked by FXMLLoader after all {@code @FXML} fields are injected.
     */
    @FXML
    public void initialize() {
        if (timeWindowCombo != null) {
            timeWindowCombo.setItems(FXCollections.observableArrayList(TimeWindow.values()));
            timeWindowCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(TimeWindow object) {
                    return object == null ? "" : object.toString();
                }

                @Override
                public TimeWindow fromString(String string) {
                    return null;
                }
            });
            timeWindowCombo.getSelectionModel().select(TimeWindow.LAST_7_DAYS);
        }

        if (actionFilterCombo != null) {
            actionFilterCombo.setItems(FXCollections.observableArrayList());
            actionFilterCombo.getItems().add(ALL_ACTIONS);
            actionFilterCombo.getItems().addAll(AuditService.supportedActionNames());
            actionFilterCombo.getSelectionModel().select(ALL_ACTIONS);
        }

        if (auditTable != null) {
            auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        }

        if (timestampColumn != null) {
            timestampColumn.setCellValueFactory(data -> {
                LocalDateTime timestamp = data.getValue().getTimestamp();
                return new SimpleStringProperty(timestamp != null ? timestamp.format(TS_FMT) : "\u2014");
            });
        }
        if (usernameColumn != null) {
            usernameColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(data.getValue().getUsername() != null
                            ? data.getValue().getUsername() : "\u2014"));
        }
        if (actionColumn != null) {
            actionColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(data.getValue().getAction() != null
                            ? data.getValue().getAction() : "\u2014"));
        }
        if (entityColumn != null) {
            entityColumn.setCellValueFactory(data -> new SimpleStringProperty(formatEntity(data.getValue())));
        }
        if (detailsColumn != null) {
            detailsColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(data.getValue().getDetails() != null
                            ? data.getValue().getDetails() : "{}"));
        }
    }

    /**
     * Sets the authenticated admin user and triggers an initial data load.
     *
     * @param admin currently logged-in user; must have ADMIN role
     * @throws SecurityException if the user is null or does not have ADMIN role
     */
    public void setAdminUser(User admin) {
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
        this.currentAdminUser = admin;
        if (isTabActive.get() && auditTable != null) {
            refreshAuditLogsAsync();
        }
    }

    /**
     * Registers a supplier that indicates whether this tab is currently active.
     * Used to defer data loading when the tab is not visible.
     *
     * @param isActive supplier returning {@code true} when this tab is selected; must not be null
     */
    public void setTabActiveCheck(Supplier<Boolean> isActive) {
        this.isTabActive = isActive != null ? isActive : () -> true;
    }

    /**
     * Called by the parent dashboard controller when this tab becomes the selected tab.
     * Refreshes audit log data if an admin user has been set.
     */
    public void onTabActivated() {
        if (currentAdminUser != null && isTabActive.get()) {
            refreshAuditLogsAsync();
        }
    }

    @FXML
    private void handleRefresh() {
        refreshAuditLogsAsync();
    }

    /**
     * Loads audit log entries filtered by the given parameters.
     * Package-private to allow direct invocation from unit tests.
     *
     * @param window        time window to query; defaults to {@code LAST_7_DAYS} when null
     * @param actionFilter  action name to filter by, or {@code null} / "All Actions" for no filter
     * @param usernameFilter username to filter by (case-insensitive), or null for no filter
     * @return filtered list of audit log entries, sorted by timestamp descending
     * @throws SQLException if the database query fails
     */
    List<AuditLog> loadAuditEntries(TimeWindow window, String actionFilter, String usernameFilter)
            throws SQLException {
        ensureAdminUser();
        TimeWindow effectiveWindow = window != null ? window : TimeWindow.LAST_7_DAYS;
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = effectiveWindow.startFrom(to);
        String normalizedAction = normalizeActionFilter(actionFilter);
        return auditService.getAuditLogs(from, to, normalizedAction, usernameFilter, DEFAULT_LIMIT);
    }

    /**
     * Builds a human-readable status message for the given list of audit entries.
     * Package-private to allow unit testing without a JavaFX runtime.
     *
     * @param entries list of loaded audit log entries; may be null or empty
     * @return status message describing the number of entries loaded
     */
    String buildStatusMessage(List<AuditLog> entries) {
        if (entries == null || entries.isEmpty()) {
            return "No audit entries found.";
        }
        return "Loaded " + entries.size() + " audit entries.";
    }

    private void refreshAuditLogsAsync() {
        ensureAdminUser();
        if (auditStatusLabel != null) {
            auditStatusLabel.setText("Loading...");
        }
        TimeWindow selectedWindow = timeWindowCombo != null
                ? timeWindowCombo.getSelectionModel().getSelectedItem()
                : TimeWindow.LAST_7_DAYS;
        String selectedAction = actionFilterCombo != null
                ? actionFilterCombo.getSelectionModel().getSelectedItem()
                : null;
        String usernameFilter = usernameFilterField != null ? usernameFilterField.getText() : null;

        User capturedAdmin = currentAdminUser;
        Task<List<AuditLog>> task = new Task<>() {
            @Override
            protected List<AuditLog> call() throws Exception {
                SessionContext.setCurrentUser(capturedAdmin);
                try {
                    return loadAuditEntries(selectedWindow, selectedAction, usernameFilter);
                } finally {
                    SessionContext.clearCurrentUser();
                }
            }
        };
        task.setOnSucceeded(event -> {
            if (auditTable != null) {
                auditTable.setItems(FXCollections.observableArrayList(task.getValue()));
            }
            if (auditStatusLabel != null) {
                auditStatusLabel.setText(buildStatusMessage(task.getValue()));
            }
        });
        task.setOnFailed(event -> {
            LOGGER.error("Failed to load audit log entries", task.getException());
            if (auditStatusLabel != null) {
                auditStatusLabel.setText("Error loading audit entries.");
            }
        });
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private static String formatEntity(AuditLog auditLog) {
        if (auditLog.getEntityType() == null) {
            return "\u2014";
        }
        if (auditLog.getEntityId() == null) {
            return auditLog.getEntityType();
        }
        return auditLog.getEntityType() + "#" + auditLog.getEntityId();
    }

    private static String normalizeActionFilter(String actionFilter) {
        if (actionFilter == null || actionFilter.isBlank() || ALL_ACTIONS.equals(actionFilter)) {
            return null;
        }
        return actionFilter;
    }

    private void ensureAdminUser() {
        if (currentAdminUser == null || !"ADMIN".equalsIgnoreCase(currentAdminUser.getRole())) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
    }
}
