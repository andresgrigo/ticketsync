package com.ticketsync.controller;

import com.ticketsync.App;
import com.ticketsync.model.Event;
import com.ticketsync.model.Seat;
import com.ticketsync.model.Zone;
import com.ticketsync.model.User;
import com.ticketsync.service.AuthenticationService;
import com.ticketsync.service.EventService;
import com.ticketsync.service.FilesystemTicketSaver;
import com.ticketsync.service.PurchaseReceiptDetails;
import com.ticketsync.service.SaleLookupService;
import com.ticketsync.service.SeatService;
import com.ticketsync.service.SeatSyncService;
import com.ticketsync.service.SessionContext;
import com.ticketsync.service.TicketGenerator;
import com.ticketsync.service.TransactionService;
import com.ticketsync.util.DialogThemeHelper;
import com.ticketsync.util.ThemeStyleHelper;
import com.ticketsync.viewmodel.PosViewModel;
import com.ticketsync.viewmodel.SeatMapViewModel;
import com.ticketsync.viewmodel.SelectionPanelViewModel;
import javafx.animation.PauseTransition;
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
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controlador FXML para la vista del POS del Vendedor ({@code PosView.fxml}).
 *
 * <p>Carga eventos activos de forma asíncrona en un hilo daemon en segundo plano (el hilo
 * de Aplicación FX nunca se bloquea), conecta el {@link PosViewModel} al
 * {@code ComboBox} editable para filtrado en tiempo real, y registra atajos de teclado
 * F1–F12 que seleccionan eventos por índice.
 *
 * <p>El controlador descendente ("Vista Principal del POS con Mapa de Asientos") obtendrá el mismo
 * view-model mediante {@link #getViewModel()} para observar
 * {@link PosViewModel#selectedEventProperty()} y gestionar la carga de asientos.
 */
public class PosController {

    /** Crea una nueva instancia de {@code PosController} (invocada por FXMLLoader mediante reflexión). */
    public PosController() {
    }

    private static final Logger LOGGER= LogManager.getLogger(PosController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    @FXML private BorderPane root;
    @FXML private TextField eventSearchField;
    @FXML private ComboBox<Event> eventComboBox;
    @FXML private Label eventStatusLabel;
    @FXML private HBox systemHealthBanner;
    @FXML private Label systemHealthBannerLabel;
    @FXML private Label noEventsLabel;
    @FXML private Label eventContextLabel;
    @FXML private Label availableSeatsContextLabel;
    @FXML private Label boothContextLabel;
    
    @FXML private Label lastSyncContextLabel;
    @FXML private Label systemHealthBadgeLabel;
    @FXML private Label vendorInfoLabel;
    @FXML private MenuItem ticketsDirectoryMenuItem;
    @FXML private Button logoutButton;
    @FXML private StackPane seatMapContainer;
    @FXML private StackPane selectionPanelContainer;

    private PosViewModel viewModel;
    private final EventService eventService = new EventService();
    private final SeatService seatService = new SeatService();
    private final TransactionService transactionService = new TransactionService();
    private final SaleLookupService saleLookupService = new SaleLookupService();
    private final TicketGenerator ticketGenerator = new TicketGenerator();
    private final FilesystemTicketSaver filesystemTicketSaver = new FilesystemTicketSaver();
    private final PosTicketDeliveryCoordinator ticketDeliveryCoordinator = new PosTicketDeliveryCoordinator(
            saleLookupService::getSaleItemsBySaleId,
            ticketGenerator::generateTicket,
            filesystemTicketSaver::saveTicket
    );
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
    private final PauseTransition restoredBannerPause = new PauseTransition(Duration.seconds(4));

    /**
     * Inicializa la vista del POS en el hilo de Aplicación FX.
     *
     * <p>Lee el usuario autenticado de {@link SessionContext} (seguro aquí porque
     * {@code LoginController.navigateToRoleView()} establece el contexto en el hilo FX
     * antes de cargar este FXML), configura el ComboBox, enlaza etiquetas,
     * adjunta atajos F1–F12, e inicia la carga asíncrona de eventos.
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
        configureHelpMenu();
        bindContextState();
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) {
                dispose();
            }
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleFunctionKeyShortcut);

        loadActiveEventsAsync();
    }

    private void configureHelpMenu() {
        if (ticketsDirectoryMenuItem != null) {
            ticketsDirectoryMenuItem.setText("Tickets Folder: " + filesystemTicketSaver.getTicketsRootDirectory());
        }
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
        seatMapController.bindInteractionEnabled(viewModel.purchaseEnabledProperty());

        FXMLLoader selectionPanelLoader = new FXMLLoader(App.class.getResource("SelectionPanelView.fxml"));
        selectionPanelContainer.getChildren().clear();
        selectionPanelContainer.getChildren().add(selectionPanelLoader.load());
        selectionPanelController = selectionPanelLoader.getController();
        selectionPanelController.setViewModel(selectionPanelViewModel);
        selectionPanelController.bindConfirmPurchaseTooltip(viewModel.purchaseBlockedReasonTextProperty());

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
     * Configura el selector de eventos: un {@link TextField} separado gestiona el filtrado
     * mientras que un {@link ComboBox} no editable gestiona la selección.
     *
     * <p>Mantener los dos controles separados es el único patrón confiable en JavaFX 21:
     * un ComboBox editable con una lista de elementos mutable falla con
     * {@code IndexOutOfBoundsException} porque el {@code StringConverter} actualiza
     * el texto del editor de forma síncrona durante el despacho del clic del popup, mutando
     * la lista de elementos mientras {@code ListViewBehavior} aún mantiene una referencia al
     * tamaño anterior. Un {@code TextField} independiente dispara su listener solo en pulsaciones
     * del usuario, nunca durante un clic del ComboBox, por lo que los dos flujos de eventos
     * nunca colisionan.
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

        // Sincronizar la selección del ComboBox en el viewModel mediante listener (no .bind()) para que la propiedad
        // permanezca escribible para los consumidores posteriores.
        eventComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                viewModel.selectedEventProperty().set(newVal));

        // Escribir en el campo de búsqueda filtra los elementos del ComboBox y abre/cierra el popup.
        // Este listener está completamente desacoplado de los eventos de clic del ComboBox.
        eventSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.filterEvents(newVal);
            if (newVal.isEmpty()) {
                eventComboBox.hide();
            } else if (!viewModel.getFilteredEvents().isEmpty()) {
                eventComboBox.show();
            }
        });

            // Después de confirmar una selección, limpiar el campo de búsqueda para que la lista completa
            // sea visible en la siguiente apertura. Diferido para permitir que el popup se cierre primero.
        eventComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Platform.runLater(eventSearchField::clear);
            }
        });
    }

    /**
     * Enlaza el texto de {@code selectedEventLabel} al evento actualmente seleccionado y
     * establece el estado inicial oculto de {@code noEventsLabel}.
     */
    private void bindLabels() {
        eventContextLabel.textProperty().bind(viewModel.selectedEventTextProperty());
        availableSeatsContextLabel.textProperty().bind(viewModel.availableSeatCountTextProperty());
        boothContextLabel.textProperty().bind(viewModel.boothIdTextProperty());
        lastSyncContextLabel.textProperty().bind(viewModel.lastSyncTimestampTextProperty());
        systemHealthBadgeLabel.textProperty().bind(viewModel.systemHealthBadgeTextProperty());
        systemHealthBannerLabel.textProperty().bind(viewModel.systemHealthBannerTextProperty());

        // La visibilidad de noEventsLabel es controlada por loadActiveEventsAsync tras completar la carga
        noEventsLabel.setVisible(false);
        noEventsLabel.setManaged(false);
    }

    private void bindContextState() {
        systemHealthBanner.visibleProperty().bind(viewModel.systemHealthBannerVisibleProperty());
        systemHealthBanner.managedProperty().bind(viewModel.systemHealthBannerVisibleProperty());
        applySystemHealthPresentation(viewModel.systemHealthStateProperty().get());
        viewModel.systemHealthStateProperty().addListener((obs, oldValue, newValue) -> {
            applySystemHealthPresentation(newValue);
            if (newValue == PosViewModel.SystemHealthState.RESTORED) {
                restoredBannerPause.stop();
                restoredBannerPause.setOnFinished(event -> viewModel.acknowledgeRestoredState());
                restoredBannerPause.playFromStart();
            } else {
                restoredBannerPause.stop();
            }
        });
        viewModel.purchaseEnabledProperty().addListener((obs, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(oldValue) && Boolean.FALSE.equals(newValue) && posScreenCoordinator != null) {
                posScreenCoordinator.enterFailSafeMode();
            }
        });
    }

    /**
     * Carga eventos activos en un hilo daemon en segundo plano.
     *
     * <p>El {@code ThreadLocal} de {@link SessionContext} se captura en el hilo FX
     * y se inyecta en el hilo de la {@code Task} — el mismo patrón de captura e inyección
     * utilizado en ({@code AdminDashboardController}).
     * El hilo de Aplicación FX nunca se bloquea.
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
     * Maneja eventos de teclado F1–F12, seleccionando el evento en el índice de base cero
     * correspondiente de la lista filtrada actual.
     *
     * <p>Presionar F<em>n</em> cuando hay menos de <em>n</em> eventos presentes
     * no realiza ninguna acción. El evento de tecla se consume para suprimir el
     * comportamiento predeterminado del SO (por ejemplo, enfocar el panel de ayuda del navegador).
     *
     * @param event el evento de tecla presionada capturado por el filtro de eventos de la escena
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
     * Maneja el botón de Cierre de sesión: limpia la sesión y navega de regreso a la pantalla de inicio de sesión.
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
     * Retorna el {@link PosViewModel} para consumo por los controladores posteriores
     * (por ejemplo, integración del mapa de asientos).
     *
     * @return la instancia del view-model; nunca {@code null} después de una inicialización exitosa
     */
    public PosViewModel getViewModel() {
        return viewModel;
    }

    /**
     * Libera los recursos en segundo plano mantenidos por este controlador.
     *
     * <p>Detiene el coordinador de sincronización de asientos, destruye el controlador
     * del panel de selección y apaga el ejecutor en segundo plano. Es seguro llamarlo
     * varias veces; las llamadas posteriores a la primera son ignoradas.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        restoredBannerPause.stop();
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
            showReceiptDialog(success);
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
        DialogThemeHelper.apply(alert);
        alert.setTitle("Purchase Unavailable");
        alert.setHeaderText("Purchase could not be completed");
        alert.setContentText("Please try the purchase again.");
        alert.showAndWait();
    }

    private void showReceiptDialog(PosPurchaseCoordinator.PurchaseSuccess success) {
        PurchaseReceiptDetails receiptDetails = success.receiptDetails();
        ButtonType saveButton = new ButtonType("Save Ticket PDF", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        DialogThemeHelper.apply(alert);
        alert.setTitle("Purchase Confirmed");
        alert.setHeaderText("Purchase Confirmed");
        alert.getButtonTypes().setAll(saveButton, closeButton);
        alert.setContentText(buildReceiptContent(receiptDetails));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == saveButton) {
            handleSaveTicketRequested(success);
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

    private void handleSaveTicketRequested(PosPurchaseCoordinator.PurchaseSuccess success) {
        if (disposed) {
            return;
        }

        String transactionId = success.receiptDetails().transactionId();
        Task<PosTicketDeliveryCoordinator.DeliveryOutcome> task =
                createSessionAwareTask(() -> ticketDeliveryCoordinator.execute(success));
        task.setOnSucceeded(event -> {
            if (disposed) {
                return;
            }
            PosTicketDeliveryCoordinator.DeliveryOutcome outcome = task.getValue();
            if (outcome instanceof PosTicketDeliveryCoordinator.TicketSavedToFile saved) {
                showPrintInfo(
                        "Ticket Saved",
                        "Ticket PDF stored",
                        saved.operatorMessage()
                );
            } else if (outcome instanceof PosTicketDeliveryCoordinator.TicketSaveFailed failed) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Ticket Save Failed",
                        "Ticket could not be preserved",
                        failed.operatorMessage()
                );
            }
        });
        task.setOnFailed(event -> {
            LOGGER.error("Ticket PDF save failed for {}", transactionId, task.getException());
            if (!disposed) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Ticket Save Failed",
                        "Ticket could not be preserved",
                        "Ticket PDF could not be saved for " + transactionId
                                + ". Please contact support before handing tickets to the customer."
                );
            }
        });
        submitTask(task);
    }

    @FXML
    private void handleShowTicketsDirectory() {
        if (disposed) {
            return;
        }
        File folder = filesystemTicketSaver.getTicketsRootDirectory().toFile();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            try {
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                Desktop.getDesktop().open(folder);
            } catch (IOException e) {
                LOGGER.warn("Could not open tickets directory in file manager: {}", folder, e);
                showPrintInfo("Ticket Folder", "Saved tickets folder", folder.toString());
            }
        } else {
            showPrintInfo("Ticket Folder", "Saved tickets folder", folder.toString());
        }
    }

    private void showPrintInfo(String title, String headerText, String contentText) {
        showAlert(Alert.AlertType.INFORMATION, title, headerText, contentText);
    }

    private void showAlert(Alert.AlertType alertType, String title, String headerText, String contentText) {
        Alert alert = new Alert(alertType);
        DialogThemeHelper.apply(alert);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    private void showConflictDialog(PosPurchaseCoordinator.PurchaseConflict conflict) {
        ButtonType filterButton = new ButtonType(conflict.primaryActionLabel(), ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        DialogThemeHelper.apply(alert);
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

    private void applySystemHealthPresentation(PosViewModel.SystemHealthState state) {
        applySystemHealthBadgeStyle(state);
        applySystemHealthBannerStyle(state);
    }

    private void applySystemHealthBadgeStyle(PosViewModel.SystemHealthState state) {
        ThemeStyleHelper.applyManagedStateClass(
                systemHealthBadgeLabel.getStyleClass(),
                "system-health-badge",
                ThemeStyleHelper.HEALTH_STATE_CLASSES,
                ThemeStyleHelper.healthStateClass(state)
        );
    }

    private void applySystemHealthBannerStyle(PosViewModel.SystemHealthState state) {
        ThemeStyleHelper.applyManagedStateClass(
                systemHealthBanner.getStyleClass(),
                "system-health-banner",
                ThemeStyleHelper.HEALTH_STATE_CLASSES,
                ThemeStyleHelper.healthStateClass(state)
        );
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
