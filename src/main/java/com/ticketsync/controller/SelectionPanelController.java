package com.ticketsync.controller;

import com.ticketsync.viewmodel.SelectionPanelViewModel;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.ticketsync.util.ThemeStyleHelper;
import java.util.Objects;

/**
 * Controlador FXML para la barra lateral del panel de selección mostrada en la vista del POS.
 *
 * <p>Muestra los asientos seleccionados actualmente, el precio total, la cuenta regresiva de bloqueo
 * y las acciones de confirmación/liberación. Enlaza sus elementos de UI a un {@link SelectionPanelViewModel}.
 */
public class SelectionPanelController {

    /** Crea un nuevo SelectionPanelController; instanciado por FXMLLoader. */
    public SelectionPanelController() { }

    @FXML private VBox rootContainer;
    @FXML private Label headerLabel;
    @FXML private Label emptyStateLabel;
    @FXML private VBox selectionContent;
    @FXML private ListView<String> selectedSeatsListView;
    @FXML private Label totalPriceLabel;
    @FXML private Label countdownLabel;
    @FXML private StackPane confirmPurchaseHelpTarget;
    @FXML private Button confirmPurchaseButton;
    @FXML private Button releaseLockButton;
    @FXML private StackPane processingOverlay;

    private final Tooltip confirmPurchaseTooltip = new Tooltip();
    private final ChangeListener<String> confirmPurchaseTooltipListener =
            (obs, oldValue, newValue) -> applyConfirmPurchaseTooltip(newValue);
    private final ChangeListener<Boolean> countdownWarningListener =
            (obs, oldValue, newValue) -> applyCountdownWarningState(Boolean.TRUE.equals(newValue));

    private SelectionPanelViewModel viewModel;
    private ObservableStringValue confirmPurchaseTooltipSource;

    /**
     * Inicializa el controlador después de la inyección FXML.
     *
     * <p>Configura la vista de lista de asientos para que no sea navegable por foco, de modo que
     * el foco de teclado permanezca en el lienzo del mapa de asientos.
     */
    @FXML
    public void initialize() {
        selectedSeatsListView.setFocusTraversable(false);
    }

    /**
     * Enlaza todos los controles de UI al view-model dado, reemplazando cualquier enlace previo.
     *
     * @param viewModel el view-model a enlazar; no debe ser {@code null}
     * @throws NullPointerException si {@code viewModel} es {@code null}
     */
    public void setViewModel(SelectionPanelViewModel viewModel) {
        SelectionPanelViewModel newViewModel = Objects.requireNonNull(viewModel, "viewModel must not be null");
        unbindCurrentViewModel();
        this.viewModel = newViewModel;

        headerLabel.textProperty().bind(newViewModel.headerTextProperty());
        totalPriceLabel.textProperty().bind(newViewModel.totalPriceTextProperty());
        countdownLabel.textProperty().bind(newViewModel.countdownTextProperty());
        newViewModel.warningStateProperty().addListener(countdownWarningListener);
        applyCountdownWarningState(newViewModel.warningStateProperty().get());

        emptyStateLabel.visibleProperty().bind(newViewModel.emptyStateVisibleProperty());
        emptyStateLabel.managedProperty().bind(newViewModel.emptyStateVisibleProperty());
        selectionContent.visibleProperty().bind(newViewModel.emptyStateVisibleProperty().not());
        selectionContent.managedProperty().bind(newViewModel.emptyStateVisibleProperty().not());
        processingOverlay.visibleProperty().bind(newViewModel.processingProperty());
        processingOverlay.managedProperty().bind(newViewModel.processingProperty());
        confirmPurchaseButton.disableProperty().bind(newViewModel.confirmEnabledProperty().not());
        releaseLockButton.disableProperty().bind(newViewModel.releaseEnabledProperty().not());
        selectedSeatsListView.setItems(newViewModel.selectedSeatRowsProperty());
    }

    /**
     * Retorna el view-model actualmente enlazado a este panel.
     *
     * @return el view-model actual; puede ser {@code null} antes de que se llame {@link #setViewModel}
     */
    public SelectionPanelViewModel getViewModel() {
        return viewModel;
    }

    /**
     * Se suscribe al string observable dado para mostrarlo como tooltip del botón de confirmación de compra.
     *
     * <p>Pasar {@code null} para eliminar cualquier fuente de tooltip previamente instalada.
     *
     * @param tooltipText observable que proporciona el texto del tooltip; puede ser {@code null}
     */
    public void bindConfirmPurchaseTooltip(ObservableStringValue tooltipText) {
        if (confirmPurchaseTooltipSource != null) {
            confirmPurchaseTooltipSource.removeListener(confirmPurchaseTooltipListener);
        }
        confirmPurchaseTooltipSource = tooltipText;
        if (confirmPurchaseTooltipSource != null) {
            confirmPurchaseTooltipSource.addListener(confirmPurchaseTooltipListener);
            applyConfirmPurchaseTooltip(confirmPurchaseTooltipSource.getValue());
        } else {
            applyConfirmPurchaseTooltip("");
        }
    }

    /**
     * Desenlaza todas las propiedades de UI, destruye el view-model actual y elimina las suscripciones de tooltip.
     *
     * <p>Debe llamarse cuando el controlador dueño es desmontado para evitar fugas de memoria.
     */
    public void dispose() {
        unbindCurrentViewModel();
        if (viewModel != null) {
            viewModel.dispose();
        }
        bindConfirmPurchaseTooltip(null);
        viewModel = null;
    }

    @FXML
    private void handleConfirmPurchase() {
        if (viewModel != null) {
            viewModel.confirmSelection();
        }
    }

    @FXML
    private void handleReleaseLock() {
        if (viewModel != null) {
            viewModel.releaseSelection();
        }
    }

    private void unbindCurrentViewModel() {
        headerLabel.textProperty().unbind();
        totalPriceLabel.textProperty().unbind();
        countdownLabel.textProperty().unbind();
        if (viewModel != null) {
            viewModel.warningStateProperty().removeListener(countdownWarningListener);
        }
        applyCountdownWarningState(false);
        emptyStateLabel.visibleProperty().unbind();
        emptyStateLabel.managedProperty().unbind();
        selectionContent.visibleProperty().unbind();
        selectionContent.managedProperty().unbind();
        processingOverlay.visibleProperty().unbind();
        processingOverlay.managedProperty().unbind();
        confirmPurchaseButton.disableProperty().unbind();
        releaseLockButton.disableProperty().unbind();
        selectedSeatsListView.setItems(FXCollections.emptyObservableList());
    }

    private void applyConfirmPurchaseTooltip(String tooltipText) {
        String normalizedText = tooltipText == null ? "" : tooltipText.strip();
        confirmPurchaseTooltip.setText(normalizedText);
        if (normalizedText.isEmpty()) {
            Tooltip.uninstall(confirmPurchaseHelpTarget, confirmPurchaseTooltip);
        } else {
            Tooltip.install(confirmPurchaseHelpTarget, confirmPurchaseTooltip);
        }
    }

    private void applyCountdownWarningState(boolean warning) {
        ThemeStyleHelper.applyManagedStateClass(
                countdownLabel.getStyleClass(),
                "countdown-label",
                ThemeStyleHelper.COUNTDOWN_STATE_CLASSES,
                ThemeStyleHelper.countdownStateClass(warning)
        );
    }
}
