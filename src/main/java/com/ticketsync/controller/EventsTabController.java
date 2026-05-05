package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.Event;
import com.ticketsync.model.User;
import com.ticketsync.service.EventService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.util.DialogThemeHelper;
import com.ticketsync.util.ThemeStyleHelper;
import com.ticketsync.viewmodel.EventManagementViewModel;
import javafx.beans.binding.Bindings;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * FXML controller for the events management tab within the Admin Dashboard.
 *
 * <p>Displays a {@link javafx.scene.control.TableView} of {@link Event} objects and
 * provides actions to create, edit, delete, and toggle the active state of events.
 * Delegates all event CRUD operations to {@link EventService} and uses
 * {@link EventManagementViewModel} to manage observable state.
 */
public class EventsTabController {

    /** Creates a new {@code EventsTabController} instance (invoked by FXMLLoader via reflection). */
    public EventsTabController() {
    }

    private static final Logger LOGGER= LogManager.getLogger(EventsTabController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EventService eventService = new EventService();
    private EventManagementViewModel eventsViewModel;
    private User currentAdminUser;

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
     * <p>Configures table column cell-value factories, status cell styling,
     * binds the events table to the view-model observable list, and wires
     * button enabled states to the table selection.
     */
    @FXML
    public void initialize() {
        eventsViewModel = new EventManagementViewModel();

        eventNameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName() != null
                        ? data.getValue().getName() : ""));
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
                ThemeStyleHelper.applyManagedStateClass(
                        getStyleClass(),
                        "status-table-cell",
                        ThemeStyleHelper.STATUS_STATE_CLASSES,
                        null
                );
                if (empty || val == null) {
                    setText(null);
                } else {
                    boolean active = "Active".equals(val);
                    setText(active ? "Active" : "Inactive");
                    ThemeStyleHelper.applyManagedStateClass(
                            getStyleClass(),
                            "status-table-cell",
                            ThemeStyleHelper.STATUS_STATE_CLASSES,
                            ThemeStyleHelper.eventStatusClass(active)
                    );
                }
            }
        });

        eventsTable.setItems(eventsViewModel.eventsProperty());
        eventsViewModel.selectedEventProperty().bind(
                eventsTable.getSelectionModel().selectedItemProperty());

        javafx.beans.binding.BooleanBinding noEventSelection =
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
                        eventsTable.getSelectionModel().selectedItemProperty()));
    }

    /**
     * Called by the shell controller once the admin identity is known.
     *
     * @param admin the authenticated administrator user; must not be {@code null}
     */
    public void setAdminUser(User admin) {
        this.currentAdminUser = admin;
        loadEventsAsync();
    }

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

    @FXML
    private void handleCreateEvent() {
        FXMLLoader loader = createEventFormLoader();
        if (loader == null) return;

        EventFormController formController = loader.getController();
        formController.setMode(EventFormController.Mode.CREATE);

        Dialog<ButtonType> dialog = buildEventDialog("Create Event", loader.getRoot());
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION,
                event -> { if (!formController.validate()) event.consume(); });

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
                showSecurityAwareError("Create Event Failed", ex);
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    @FXML
    private void handleEditEvent() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null) return;

        FXMLLoader loader = createEventFormLoader();
        if (loader == null) return;

        EventFormController formController = loader.getController();
        formController.setMode(EventFormController.Mode.EDIT);
        formController.setEvent(selectedEvent);

        Dialog<ButtonType> dialog = buildEventDialog("Edit Event", loader.getRoot());
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(ActionEvent.ACTION,
                event -> { if (!formController.validate()) event.consume(); });

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
                LOGGER.error("Edit event failed", task.getException());
                showSecurityAwareError("Edit Event Failed", task.getException());
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    @FXML
    private void handleDeleteEvent() {
        Event selectedEvent = eventsViewModel.selectedEventProperty().get();
        if (selectedEvent == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogThemeHelper.apply(confirm);
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
                LOGGER.error("Delete event failed", task.getException());
                showSecurityAwareError("Delete Event Failed", task.getException());
            });
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

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
            LOGGER.error("Toggle activate event failed", task.getException());
            showSecurityAwareError("Operation Failed", task.getException());
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FXMLLoader createEventFormLoader() {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("EventFormView.fxml"));
            loader.load();
            return loader;
        } catch (IOException ex) {
            LOGGER.error("Failed to load EventFormView.fxml", ex);
            showErrorAlert("Internal Error", "Could not open the event form. Please try again.");
            return null;
        }
    }

    private Dialog<ButtonType> buildEventDialog(String title, Parent content) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogThemeHelper.apply(dialog);
        return dialog;
    }

    private void showSecurityAwareError(String baseTitle, Throwable ex) {
        String title = (ex instanceof SecurityException) ? "Access Denied" : baseTitle;
        String msg = (ex instanceof SecurityException) ? "ADMIN role is required."
                : "Operation failed. Please try again.";
        showErrorAlert(title, msg);
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
