package com.ticketsync.controller;

import com.ticketsync.viewmodel.SelectionPanelViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

public class SelectionPanelController {

    private static final String NORMAL_COUNTDOWN_STYLE = "-fx-text-fill: #616161;";
    private static final String WARNING_COUNTDOWN_STYLE = "-fx-text-fill: #F9A825; -fx-font-weight: bold;";

    @FXML private VBox rootContainer;
    @FXML private Label headerLabel;
    @FXML private Label emptyStateLabel;
    @FXML private VBox selectionContent;
    @FXML private ListView<String> selectedSeatsListView;
    @FXML private Label totalPriceLabel;
    @FXML private Label countdownLabel;
    @FXML private Button confirmPurchaseButton;
    @FXML private Button releaseLockButton;
    @FXML private StackPane processingOverlay;

    private SelectionPanelViewModel viewModel;

    @FXML
    public void initialize() {
        selectedSeatsListView.setFocusTraversable(false);
    }

    public void setViewModel(SelectionPanelViewModel viewModel) {
        SelectionPanelViewModel newViewModel = Objects.requireNonNull(viewModel, "viewModel must not be null");
        unbindCurrentViewModel();
        this.viewModel = newViewModel;

        headerLabel.textProperty().bind(newViewModel.headerTextProperty());
        totalPriceLabel.textProperty().bind(newViewModel.totalPriceTextProperty());
        countdownLabel.textProperty().bind(newViewModel.countdownTextProperty());
        countdownLabel.styleProperty().bind(Bindings.createStringBinding(
                () -> newViewModel.warningStateProperty().get() ? WARNING_COUNTDOWN_STYLE : NORMAL_COUNTDOWN_STYLE,
                newViewModel.warningStateProperty()
        ));

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

    public SelectionPanelViewModel getViewModel() {
        return viewModel;
    }

    public void dispose() {
        unbindCurrentViewModel();
        if (viewModel != null) {
            viewModel.dispose();
        }
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
        countdownLabel.styleProperty().unbind();
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
}
