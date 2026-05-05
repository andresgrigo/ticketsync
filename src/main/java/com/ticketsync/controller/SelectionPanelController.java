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
 * FXML controller for the selection panel sidebar shown in the POS view.
 *
 * <p>Displays the currently selected seats, total price, lock countdown, and
 * confirmation/release actions. Binds its UI elements to a {@link SelectionPanelViewModel}.
 */
public class SelectionPanelController {

    /** Creates a new SelectionPanelController; instantiated by FXMLLoader. */
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
     * Initialises the controller after FXML injection.
     *
     * <p>Configures the seat list view to be non-focus-traversable so that
     * keyboard focus stays on the seat-map canvas.
     */
    @FXML
    public void initialize() {
        selectedSeatsListView.setFocusTraversable(false);
    }

    /**
     * Binds all UI controls to the given view-model, replacing any previous binding.
     *
     * @param viewModel the view-model to bind; must not be {@code null}
     * @throws NullPointerException if {@code viewModel} is {@code null}
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
     * Returns the view-model currently bound to this panel.
     *
     * @return the current view-model; may be {@code null} before {@link #setViewModel} is called
     */
    public SelectionPanelViewModel getViewModel() {
        return viewModel;
    }

    /**
     * Subscribes to the given observable string to show as the confirm-purchase button tooltip.
     *
     * <p>Pass {@code null} to remove any previously installed tooltip source.
     *
     * @param tooltipText observable providing the tooltip text; may be {@code null}
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
     * Unbinds all UI properties, disposes the current view-model, and removes tooltip subscriptions.
     *
     * <p>Must be called when the owning controller is torn down to avoid memory leaks.
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
