package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.Zone;
import com.ticketsync.model.User;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.EventService;
import com.ticketsync.service.PurchaseReceiptDetails;
import com.ticketsync.service.SaleLookupService;
import com.ticketsync.service.SeatService;
import com.ticketsync.service.SeatSyncService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.service.TransactionService;
import com.ticketsync.viewmodel.PosViewModel;
import com.ticketsync.viewmodel.SeatMapViewModel;
import com.ticketsync.viewmodel.SelectionPanelViewModel;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String HEALTHY_BADGE_STYLE =
            "-fx-background-color: #E8F5E9; -fx-text-fill: #2E7D32; -fx-background-radius: 16; "
                    + "-fx-padding: 6 12 6 12; -fx-font-weight: bold;";
    private static final String UNHEALTHY_BADGE_STYLE =
            "-fx-background-color: #F5F5F5; -fx-text-fill: #616161; -fx-background-radius: 16; "
                    + "-fx-padding: 6 12 6 12; -fx-font-weight: bold;";

    @FXML private BorderPane root;
    @FXML private TextField eventSearchField;
    @FXML private ComboBox<Event> eventComboBox;
    @FXML private Label eventStatusLabel;
    @FXML private Label noEventsLabel;
    @FXML private Label eventContextLabel;
    @FXML private Label availableSeatsContextLabel;
    @FXML private Label boothContextLabel;
    
    @FXML private Label lastSyncContextLabel;
    @FXML private Label systemHealthBadgeLabel;
    @FXML private Label vendorInfoLabel;
    @FXML private Button logoutButton;
    @FXML private StackPane seatMapContainer;
    @FXML private StackPane selectionPanelContainer;

    private PosViewModel viewModel;
    private final EventService eventService = new EventService();
    private final SeatService seatService = new SeatService();
    private final TransactionService transactionService = new TransactionService();
    private final SaleLookupService saleLookupService = new SaleLookupService();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "POS-Background");
        thread.setDaemon(true);
        return thread;
    });
    private User currentUser;
    private SeatMapViewModel seatMapViewModel;
    private SelectionPanelViewModel selectionPanelViewModel;
    private SeatMapController seatMapController;
    private SelectionPanelController selectionPanelController;
    private PosScreenCoordinator posScreenCoordinator;
    private PosPurchaseCoordinator purchaseCoordinator;
    private boolean disposed;
    private int currentLoadGeneration;

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
        viewModel.setBoothId(deriveBoothId(currentUser));

        try {
            configureComposedScreen();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to compose POS screen", ex);
        }

        configureComboBox();
        bindLabels();
        bindContextState();
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                dispose();
            }
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleFunctionKeyShortcut);

        loadActiveEventsAsync();
    }

    private void configureComposedScreen() throws IOException {
        seatMapViewModel = new SeatMapViewModel();
        selectionPanelViewModel = new SelectionPanelViewModel(seatMapViewModel);
        selectionPanelViewModel.bindPurchaseEnabled(viewModel.purchaseEnabledProperty());
        selectionPanelViewModel.setOnConfirmAction(this::handleConfirmSelectionRequested);

        FXMLLoader seatMapLoader = new FXMLLoader(App.class.getResource("SeatMapView.fxml"));
        seatMapContainer.getChildren().clear();
        seatMapContainer.getChildren().add(seatMapLoader.load());
        seatMapController = seatMapLoader.getController();
        seatMapController.setViewModel(seatMapViewModel);
        seatMapController.setViewActiveCheck(() -> !disposed);

        FXMLLoader selectionPanelLoader = new FXMLLoader(App.class.getResource("SelectionPanelView.fxml"));
        selectionPanelContainer.getChildren().clear();
        selectionPanelContainer.getChildren().add(selectionPanelLoader.load());
        selectionPanelController = selectionPanelLoader.getController();
        selectionPanelController.setViewModel(selectionPanelViewModel);

        posScreenCoordinator = new PosScreenCoordinator(
                seatMapViewModel,
                new SeatSyncService(),
                seatService::getSeatById
        );
        purchaseCoordinator = new PosPurchaseCoordinator(
                transactionService::purchaseSeats,
                posScreenCoordinator::refreshSeats,
                saleLookupService::getSaleById
        );
        viewModel.selectedEventProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !disposed) {
                loadSelectedEventAsync(newValue);
            }
        });
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
        eventContextLabel.textProperty().bind(viewModel.selectedEventTextProperty());
        availableSeatsContextLabel.textProperty().bind(viewModel.availableSeatCountTextProperty());
        boothContextLabel.textProperty().bind(viewModel.boothIdTextProperty());
        lastSyncContextLabel.textProperty().bind(viewModel.lastSyncTimestampTextProperty());
        systemHealthBadgeLabel.textProperty().bind(viewModel.systemHealthBadgeTextProperty());

        // noEventsLabel visibility is controlled by loadActiveEventsAsync after load completes
        noEventsLabel.setVisible(false);
        noEventsLabel.setManaged(false);
    }

    private void bindContextState() {
        applySystemHealthBadgeStyle(viewModel.databaseHealthyProperty().get());
        viewModel.databaseHealthyProperty().addListener((obs, oldValue, newValue) ->
                applySystemHealthBadgeStyle(newValue));
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

        Task<List<Event>> task = createSessionAwareTask(eventService::getActiveEvents);

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

        submitTask(task);
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
            dispose();
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

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (posScreenCoordinator != null) {
            posScreenCoordinator.stopSeatSync();
        }
        if (selectionPanelController != null) {
            selectionPanelController.dispose();
        }
        backgroundExecutor.shutdownNow();
    }

    private void loadSelectedEventAsync(Event event) {
        eventStatusLabel.setText("Loading seat map...");
        posScreenCoordinator.stopSeatSync();
        final int capturedGeneration = ++currentLoadGeneration;

        Task<Void> task = createSessionAwareTask(() -> {
            posScreenCoordinator.loadSelectedEvent(event);
            return null;
        });
        task.setOnSucceeded(e -> {
            if (disposed || capturedGeneration != currentLoadGeneration) {
                return;
            }
            eventStatusLabel.setText("");
            viewModel.updateAvailableSeatCount(seatMapViewModel.seatsProperty());
            viewModel.markLastSyncNow();
            posScreenCoordinator.restartSeatSync(this::refreshSeatAsync);
        });
        task.setOnFailed(e -> {
            if (disposed) {
                return;
            }
            Throwable cause = task.getException();
            if (cause instanceof SecurityException) {
                LOGGER.error("Session expired loading seats for POS — redirecting to login", cause);
                try {
                    App.setRoot("LoginView");
                } catch (IOException ioEx) {
                    LOGGER.error("Failed to redirect to LoginView after seat load failure", ioEx);
                }
                return;
            }
            LOGGER.error("Failed to load seats for event {}", event.getEventId(), cause);
            eventStatusLabel.setText("Error loading seat map. Please try again.");
        });
        submitTask(task);
    }

    private void refreshSeatAsync(int seatId) {
        if (disposed) {
            return;
        }
        Task<Void> task = createSessionAwareTask(() -> {
            posScreenCoordinator.refreshSeat(seatId);
            return null;
        });
        task.setOnSucceeded(e -> {
            if (disposed) {
                return;
            }
            viewModel.updateAvailableSeatCount(seatMapViewModel.seatsProperty());
            viewModel.markLastSyncNow();
        });
        task.setOnFailed(e -> LOGGER.warn("Failed to refresh seat {} from live sync", seatId, task.getException()));
        submitTask(task);
    }

    private void handleConfirmSelectionRequested() {
        if (disposed || purchaseCoordinator == null || seatMapViewModel == null || selectionPanelViewModel == null) {
            return;
        }

        Event selectedEvent = viewModel.selectedEventProperty().get();
        if (selectedEvent == null) {
            LOGGER.warn("Purchase requested without a selected event");
            return;
        }

        List<PosPurchaseCoordinator.SelectedSeat> selectedSeats = captureSelectedSeats();
        if (selectedSeats.isEmpty()) {
            LOGGER.warn("Purchase requested without any selected available seats");
            return;
        }

        selectionPanelViewModel.setProcessing(true);
        PosPurchaseCoordinator.PurchaseRequest request = new PosPurchaseCoordinator.PurchaseRequest(
                selectedEvent.getEventId(),
                deriveBoothId(currentUser),
                selectionPanelViewModel.totalPriceProperty().get(),
                selectedSeats
        );

        Task<PosPurchaseCoordinator.PurchaseOutcome> task = createSessionAwareTask(() -> purchaseCoordinator.execute(request));
        task.setOnSucceeded(event -> handlePurchaseOutcome(task.getValue()));
        task.setOnFailed(event -> handlePurchaseFailure(task.getException()));
        submitTask(task);
    }

    private List<PosPurchaseCoordinator.SelectedSeat> captureSelectedSeats() {
        return seatMapViewModel.seatsProperty().stream()
                .filter(seat -> seatMapViewModel.selectedSeatIdsProperty().contains(seat.getSeatId()))
                .map(this::toSelectedSeatSnapshot)
                .toList();
    }

    private PosPurchaseCoordinator.SelectedSeat toSelectedSeatSnapshot(Seat seat) {
        Zone zone = seatMapViewModel.getZone(seat.getZoneId());
        String zoneName = zone != null && zone.getName() != null && !zone.getName().isBlank()
                ? zone.getName()
                : "Unknown Zone";
        return new PosPurchaseCoordinator.SelectedSeat(
                seat.getSeatId(),
                seat.getZoneId(),
                zoneName,
                seat.getRowNumber(),
                seat.getSeatNumber()
        );
    }

    private void handlePurchaseOutcome(PosPurchaseCoordinator.PurchaseOutcome outcome) {
        if (disposed || selectionPanelViewModel == null) {
            return;
        }

        selectionPanelViewModel.resetToReadyState();
        posScreenCoordinator.clearRecoveryFilter();

        if (outcome instanceof PosPurchaseCoordinator.PurchaseSuccess success) {
            showReceiptDialog(success.receiptDetails());
            return;
        }

        if (outcome instanceof PosPurchaseCoordinator.PurchaseConflict conflict) {
            showConflictDialog(conflict);
        }
    }

    private void handlePurchaseFailure(Throwable cause) {
        if (disposed || selectionPanelViewModel == null) {
            return;
        }

        selectionPanelViewModel.setProcessing(false);
        if (cause instanceof SecurityException) {
            LOGGER.error("Session expired during purchase flow — redirecting to login", cause);
            try {
                App.setRoot("LoginView");
            } catch (IOException ioEx) {
                LOGGER.error("Failed to redirect to LoginView after purchase security failure", ioEx);
            }
            return;
        }

        LOGGER.error("Unexpected failure in purchase flow", cause);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Purchase Unavailable");
        alert.setHeaderText("Purchase could not be completed");
        alert.setContentText("Please try the purchase again.");
        alert.showAndWait();
    }

    private void showReceiptDialog(PurchaseReceiptDetails receiptDetails) {
        ButtonType printButton = new ButtonType("Print", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Purchase Confirmed");
        alert.setHeaderText("Purchase Confirmed");
        alert.getButtonTypes().setAll(printButton, closeButton);
        alert.setContentText(buildReceiptContent(receiptDetails));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == printButton) {
            LOGGER.info("Print requested for {} - deferred to Epic 6", receiptDetails.transactionId());
            Alert printAlert = new Alert(Alert.AlertType.INFORMATION);
            printAlert.setTitle("Printing Coming Soon");
            printAlert.setHeaderText("Receipt saved in TicketSync");
            printAlert.setContentText(
                    "Ticket printing will be enabled in a later story.\nTransaction: " + receiptDetails.transactionId()
            );
            printAlert.showAndWait();
        }
    }

    private String buildReceiptContent(PurchaseReceiptDetails receiptDetails) {
        String seatLines = String.join("\n", receiptDetails.seatLines());
        return "Transaction ID: " + receiptDetails.transactionId()
                + "\nTimestamp: " + receiptDetails.timestampText()
                + "\nSeats:\n" + seatLines
                + "\nTotal: " + receiptDetails.totalPriceText()
                + "\nBooth: " + receiptDetails.boothId();
    }

    private void showConflictDialog(PosPurchaseCoordinator.PurchaseConflict conflict) {
        ButtonType filterButton = new ButtonType(conflict.primaryActionLabel(), ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Seat No Longer Available");
        alert.setHeaderText("Seat No Longer Available");
        alert.getButtonTypes().setAll(filterButton, closeButton);
        alert.setContentText(conflict.message());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == filterButton) {
            posScreenCoordinator.showAvailableSeatsInZone(conflict.zoneId());
        } else {
            posScreenCoordinator.clearRecoveryFilter();
        }
    }

    private void applySystemHealthBadgeStyle(boolean healthy) {
        systemHealthBadgeLabel.setStyle(healthy ? HEALTHY_BADGE_STYLE : UNHEALTHY_BADGE_STYLE);
    }

    private void submitTask(Task<?> task) {
        if (!disposed) {
            backgroundExecutor.submit(task);
        }
    }

    private <T> Task<T> createSessionAwareTask(SessionAction<T> action) {
        User capturedUser = currentUser;
        return new Task<>() {
            @Override
            protected T call() throws Exception {
                SessionContext.setCurrentUser(capturedUser);
                try {
                    return action.run();
                } finally {
                    SessionContext.clearCurrentUser();
                }
            }
        };
    }

    private static String deriveBoothId(User user) {
        if (user.getUserId() > 0) {
            return "Booth " + user.getUserId();
        }
        return "Booth " + user.getUsername();
    }

    @FunctionalInterface
    private interface SessionAction<T> {
        T run() throws Exception;
    }
}
