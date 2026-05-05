package com.ticketsync.controller;

import com.ticketsync.model.Zone;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Controlador FXML para el diálogo de creación/edición de zona ({@code ZoneFormView.fxml}).
 *
 * <p>Opera en dos modos controlados por {@link Mode}:
 * <ul>
 *   <li>{@link Mode#CREATE} — campos vacíos; se construirá una nueva zona a partir de los valores del formulario.</li>
 *   <li>{@link Mode#EDIT} — campos prerrellenados desde una zona existente; {@code zoneId} y
 *       {@code eventId} se preservan en la zona retornada por {@link #getZoneFromForm()}.</li>
 * </ul>
 */
public class ZoneFormController {

    /** Crea un nuevo ZoneFormController; instanciado por FXMLLoader. */
    public ZoneFormController() { }

    /** Modo operacional que determina el comportamiento de prerrellenado. */
    public enum Mode {
        /** Todos los campos vacíos; se construirá una nueva zona a partir de los valores del formulario. */
        CREATE,
        /** Todos los campos prerrellenados desde una zona existente; los campos de identidad se preservan. */
        EDIT
    }

    @FXML
    private TextField nameField;

    @FXML
    private TextField priceField;

    @FXML
    private Label errorLabel;

    private Mode mode = Mode.CREATE;
    private Zone originalZone = null;

    @FXML
    private void initialize() {
        // Permitir solo dígitos y a lo sumo un punto decimal
        priceField.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*\\.?\\d*")) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Establece el modo operacional para este formulario.
     *
     * @param mode el modo a aplicar; no debe ser {@code null}
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Rellena previamente los campos del formulario con los datos de una zona existente.
     *
     * <p>Debe llamarse en {@link Mode#EDIT} después de {@link #setMode(Mode)}.
     *
     * @param zone la zona cuyos datos deben mostrarse; no debe ser {@code null}
     */
    public void setZone(Zone zone) {
        this.originalZone = zone;
        nameField.setText(zone.getName() != null ? zone.getName() : "");
        priceField.setText(zone.getPrice() != null ? zone.getPrice().toPlainString() : "");
    }

    /**
     * Valida el contenido del formulario.
     *
     * <p>Reglas de validación:
     * <ol>
     *   <li>El nombre no debe estar en blanco.</li>
     *   <li>El precio debe ser analizable como {@link BigDecimal}.</li>
     *   <li>El precio debe ser &gt; 0.</li>
     * </ol>
     *
     * @return {@code true} si todas las validaciones pasan; {@code false} en caso contrario
     */
    public boolean validate() {
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            showError("Zone name is required");
            return false;
        }
        String priceText = priceField.getText() == null ? "" : priceField.getText().strip();
        if (priceText.isEmpty()) {
            showError("Price is required");
            return false;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(priceText);
        } catch (NumberFormatException e) {
            showError("Price must be a valid decimal number");
            return false;
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            showError("Price must be greater than 0");
            return false;
        }
        hideError();
        return true;
    }

    /**
     * Retorna el nombre de zona recortado del formulario.
     *
     * <p>Solo llame después de que {@link #validate()} retorne {@code true}.
     *
     * @return la cadena de nombre recortada
     */
    public String getName() {
        return nameField.getText().strip();
    }

    /**
     * Retorna el precio del formulario como {@link BigDecimal}.
     *
     * <p>Solo llame después de que {@link #validate()} retorne {@code true}.
     *
     * @return el precio analizado
     */
    public BigDecimal getPrice() {
        return new BigDecimal(priceField.getText().strip()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Construye y retorna una {@link Zone} a partir de los valores actuales del formulario.
     *
     * <p>En {@link Mode#EDIT} la zona retornada preserva el {@code zoneId} y {@code eventId}
     * originales de la zona para que {@code ZoneService.updateZone()} pueda identificar
     * el registro a actualizar.
     *
     * @return la {@link Zone} completada; nunca {@code null}
     */
    public Zone getZoneFromForm() {
        Zone zone = new Zone();
        if (mode == Mode.EDIT && originalZone != null) {
            zone.setZoneId(originalZone.getZoneId());
            zone.setEventId(originalZone.getEventId());
        }
        zone.setName(getName());
        zone.setPrice(getPrice());
        return zone;
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setOpacity(1.0);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setOpacity(0.0);
    }
}
