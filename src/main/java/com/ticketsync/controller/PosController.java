package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.Event;
import com.ticketsync.model.User;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.EventService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.viewmodel.PosViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FXML controller for the Vendor POS view ({@code PosView.fxml}).
 *
 * <p>Loads active events asynchronously on a daemon background thread (FX
 * Application Thread is never blocked), wires the {@link PosViewModel} to the
 * editable {@code ComboBox} for real-time search filtering, and registers F1–F12
 * keyboard shortcuts that select events by index.
 *
 * <p>Downstream ("POS Main View with Seat Map") will obtain the same
 * view-model via {@link #getViewModel()} to observe
 * {@link PosViewModel#selectedEventProperty()} and drive seat loading.
 */
public class PosController {

    private static final Logger LOGGER = LogManager.getLogger(PosController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private BorderPane root;
    @FXML private TextField eventSearchField;
    @FXML private ComboBox<Event> eventComboBox;
    @FXML private Label eventStatusLabel;
    @FXML private Label noEventsLabel;
    @FXML private Label selectedEventLabel;
    @FXML private Label vendorInfoLabel;
    @FXML private Button logoutButton;

    private PosViewModel viewModel;
    private final EventService eventService = new EventService();
    private User currentUser;

    /**
     * Initialises the POS view on the FX Application Thread.
     *
     * <p>Reads the authenticated user from {@link SessionContext} (safe here
     * because {@code LoginController.navigateToRoleView()} sets the context on
     * the FX thread before loading this FXML), configures the ComboBox, binds
     * labels, attaches F1–F12 shortcuts, and starts the asynchronous event-load.
     */
    @FXML
    public void initialize() {
        currentUser = SessionContext.getCurrentUser().orElse(null);
        if (currentUser == null) {
            LOGGER.error("No authenticated user in POS view — redirecting to login");
            try {
                App.setRoot("LoginView");
            } catch (IOException ex) {
                LOGGER.error("Failed to redirect to LoginView from POS", ex);
            }
            return;
        }

        vendorInfoLabel.setText("Vendor: " + currentUser.getUsername());

        viewModel = new PosViewModel();

        configureComboBox();
        bindLabels();

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleFunctionKeyShortcut);

        loadActiveEventsAsync();
    }

    /**
     * Configures the event selector: a separate {@link TextField} drives filtering
     * while a non-editable {@link ComboBox} handles selection.
     *
     * <p>Keeping the two controls separate is the only reliable pattern in JavaFX 21:
     * an editable ComboBox with a mutable items list crashes with
     * {@code IndexOutOfBoundsException} because the {@code StringConverter} updates
     * the editor text synchronously during the popup click-dispatch, mutating the
     * items list while {@code ListViewBehavior} still holds a reference to the old
     * size. A standalone {@code TextField} fires its listener only on user keystrokes,
     * never during a ComboBox click, so the two event streams never collide.
     */
    private void configureComboBox() {
        eventComboBox.setItems(viewModel.getFilteredEvents());

        eventComboBox.setConverter(new StringConverter<Event>() {
            @Override
            public String toString(Event event) {
                if (event == null) return "";
                return event.getName() + " - " +
                       (event.getEventDate() != null ? event.getEventDate().format(DT_FMT) : "");
            }

            @Override
            public Event fromString(String s) {
                return null;
            }
        });

        eventComboBox.setCellFactory(lv -> new ListCell<Event>() {
            @Override
            protected void updateItem(Event event, boolean empty) {
                super.updateItem(event, empty);
                if (empty || event == null) {
                    setText(null);
                } else {
                    setText(event.getName() + " - " +
                            (event.getEventDate() != null ? event.getEventDate().format(DT_FMT) : ""));
                }
            }
        });

        // Sync ComboBox selection into viewModel via listener (not .bind()) so the property
        // remains writable for downstream consumers.
        eventComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                viewModel.selectedEventProperty().set(newVal));

        // Typing in the search field filters the ComboBox items and opens/closes the popup.
        // This listener is completely decoupled from ComboBox click events.
        eventSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.filterEvents(newVal);
            if (newVal.isEmpty()) {
                eventComboBox.hide();
            } else if (!viewModel.getFilteredEvents().isEmpty()) {
                eventComboBox.show();
            }
        });

        // After a selection is committed, clear the search field so the full list
        // is visible on the next open. Deferred to let the popup close first.
        eventComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Platform.runLater(eventSearchField::clear);
            }
        });
    }

    /**
     * Binds {@code selectedEventLabel} text to the currently selected event and
     * sets the initial hidden state for {@code noEventsLabel}.
     */
    private void bindLabels() {
        selectedEventLabel.textProperty().bind(
                Bindings.createStringBinding(
                        () -> {
                            Event sel = viewModel.selectedEventProperty().get();
                            return sel != null ? "Selected: " + sel.getName() : "";
                        },
                        viewModel.selectedEventProperty()
                )
        );

        // noEventsLabel visibility is controlled by loadActiveEventsAsync after load completes
        noEventsLabel.setVisible(false);
        noEventsLabel.setManaged(false);
    }

    /**
     * Loads active events on a daemon background thread.
     *
     * <p>The {@link SessionContext} {@code ThreadLocal} is captured on the FX
     * thread and injected into the {@code Task}'s thread — the same
     * capture-and-inject pattern used in ({@code AdminDashboardController}).
     * The FX Application Thread is never blocked.
     */
    private void loadActiveEventsAsync() {
        eventStatusLabel.setText("Loading events...");
        User capturedUser = currentUser;

        Task<List<Event>> task = new Task<>() {
            @Override
            protected List<Event> call() throws Exception {
                SessionContext.setCurrentUser(capturedUser);
                try {
                    return eventService.getActiveEvents();
                } finally {
                    SessionContext.clearCurrentUser();
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<Event> events = task.getValue();
            viewModel.setEvents(events);
            if (events.isEmpty()) {
                eventStatusLabel.setText("No active events available.");
                noEventsLabel.setVisible(true);
                noEventsLabel.setManaged(true);
            } else {
                eventStatusLabel.setText("");
                noEventsLabel.setVisible(false);
                noEventsLabel.setManaged(false);
            }
        });

        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            if (cause instanceof SecurityException) {
                LOGGER.error("Session expired loading events for POS — redirecting to login", cause);
                try {
                    App.setRoot("LoginView");
                } catch (IOException ioEx) {
                    LOGGER.error("Failed to redirect to LoginView after security failure", ioEx);
                }
            } else {
                LOGGER.error("Failed to load active events for POS", cause);
                eventStatusLabel.setText("Error loading events. Please try again.");
            }
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Handles F1–F12 key events, selecting the event at the corresponding
     * zero-based index in the current filtered list.
     *
     * <p>Pressing F<em>n</em> when fewer than <em>n</em> events are present
     * performs no action. The key event is consumed to suppress default OS
     * behaviour (e.g., focus the browser help panel).
     *
     * @param event the key-pressed event captured by the scene event filter
     */
    private void handleFunctionKeyShortcut(KeyEvent event) {
        int index = -1;
        switch (event.getCode()) {
            case F1  -> index = 0;
            case F2  -> index = 1;
            case F3  -> index = 2;
            case F4  -> index = 3;
            case F5  -> index = 4;
            case F6  -> index = 5;
            case F7  -> index = 6;
            case F8  -> index = 7;
            case F9  -> index = 8;
            case F10 -> index = 9;
            case F11 -> index = 10;
            case F12 -> index = 11;
            default  -> { return; }
        }
        List<Event> events = viewModel.getFilteredEvents();
        if (index < events.size()) {
            eventComboBox.getSelectionModel().select(events.get(index));
            event.consume();
        }
    }

    /**
     * Handles the Logout button: clears the session and navigates back to the login screen.
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

    /**
     * Returns the {@link PosViewModel} for consumption by downstream controllers
     * (e.g., seat-map integration).
     *
     * @return the view-model instance; never {@code null} after successful initialisation
     */
    public PosViewModel getViewModel() {
        return viewModel;
    }
}
