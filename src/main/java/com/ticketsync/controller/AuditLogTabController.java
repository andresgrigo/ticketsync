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
 * Superficie de reporte administrativo de solo lectura respaldada por la tabla {@code audit_log}.
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
     * Crea un nuevo AuditLogTabController usando el {@link AuditService} de producción.
     * Instanciado por FXMLLoader mediante reflexión.
     */
    public AuditLogTabController() {
        this(new AuditService());
    }

    AuditLogTabController(AuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    /**
     * Método de ciclo de vida FXML; enlaza las columnas de la tabla y carga los datos de auditoría iniciales.
     * Invocado por FXMLLoader después de que todos los campos {@code @FXML} son inyectados.
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
     * Establece el usuario administrador autenticado y activa una carga de datos inicial.
     *
     * @param admin usuario actualmente autenticado; debe tener rol ADMIN
     * @throws SecurityException si el usuario es nulo o no tiene rol ADMIN
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
     * Registra un proveedor que indica si esta pestaña está actualmente activa.
     * Se usa para diferir la carga de datos cuando la pestaña no es visible.
     *
     * @param isActive proveedor que retorna {@code true} cuando esta pestaña está seleccionada; no debe ser nulo
     */
    public void setTabActiveCheck(Supplier<Boolean> isActive) {
        this.isTabActive = isActive != null ? isActive : () -> true;
    }

    /**
     * Llamado por el controlador padre del panel cuando esta pestaña se convierte en la pestaña seleccionada.
     * Actualiza los datos del registro de auditoría si se ha establecido un usuario administrador.
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
     * Carga las entradas del registro de auditoría filtradas por los parámetros dados.
     * Con visibilidad de paquete para permitir la invocación directa desde pruebas unitarias.
     *
     * @param window        ventana de tiempo a consultar; por defecto {@code LAST_7_DAYS} cuando es nulo
     * @param actionFilter  nombre de acción por el que filtrar, o {@code null} / "All Actions" para sin filtro
     * @param usernameFilter nombre de usuario por el que filtrar (sin distinción de mayúsculas), o nulo para sin filtro
     * @return lista filtrada de entradas del registro de auditoría, ordenada por marca de tiempo descendente
     * @throws SQLException si la consulta a la base de datos falla
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
     * Construye un mensaje de estado legible por humanos para la lista de entradas de auditoría dada.
     * Con visibilidad de paquete para permitir pruebas unitarias sin un runtime de JavaFX.
     *
     * @param entries lista de entradas del registro de auditoría cargadas; puede ser nula o vacía
     * @return mensaje de estado que describe el número de entradas cargadas
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
