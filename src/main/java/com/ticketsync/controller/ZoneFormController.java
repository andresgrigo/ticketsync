package com.ticketsync.controller;

import com.ticketsync.model.Zone;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FXML controller for the zone create/edit dialog ({@code ZoneFormView.fxml}).
 *
 * <p>Operates in two modes controlled by {@link Mode}:
 * <ul>
 *   <li>{@link Mode#CREATE} — fields empty; a new zone will be constructed from form values.</li>
 *   <li>{@link Mode#EDIT} — fields pre-populated from an existing zone; {@code zoneId} and
 *       {@code eventId} are preserved in the zone returned from {@link #getZoneFromForm()}.</li>
 * </ul>
 */
public class ZoneFormController {

    /** Creates a new ZoneFormController; instantiated by FXMLLoader. */
    public ZoneFormController() { }

    /** Operational mode that determines pre-population behaviour. */
    public enum Mode {
        /** All fields empty; a new zone will be constructed from form values. */
        CREATE,
        /** All fields pre-populated from an existing zone; identity fields are preserved. */
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
        // Allow only digits and at most one decimal point
        priceField.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.matches("\\d*\\.?\\d*")) {
                return change;
            }
            return null;
        }));
    }

    /**
     * Sets the operational mode for this form.
     *
     * @param mode the mode to apply; must not be {@code null}
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Pre-populates the form fields with data from an existing zone.
     *
     * <p>Should be called in {@link Mode#EDIT} after {@link #setMode(Mode)}.
     *
     * @param zone the zone whose data should be displayed; must not be {@code null}
     */
    public void setZone(Zone zone) {
        this.originalZone = zone;
        nameField.setText(zone.getName() != null ? zone.getName() : "");
        priceField.setText(zone.getPrice() != null ? zone.getPrice().toPlainString() : "");
    }

    /**
     * Validates the form contents.
     *
     * <p>Validation rules:
     * <ol>
     *   <li>Name must not be blank.</li>
     *   <li>Price must be parseable as a {@link BigDecimal}.</li>
     *   <li>Price must be &gt; 0.</li>
     * </ol>
     *
     * @return {@code true} if all validations pass; {@code false} otherwise
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
     * Returns the trimmed zone name from the form.
     *
     * <p>Only call after {@link #validate()} returns {@code true}.
     *
     * @return the trimmed name string
     */
    public String getName() {
        return nameField.getText().strip();
    }

    /**
     * Returns the price from the form as a {@link BigDecimal}.
     *
     * <p>Only call after {@link #validate()} returns {@code true}.
     *
     * @return the parsed price
     */
    public BigDecimal getPrice() {
        return new BigDecimal(priceField.getText().strip()).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Constructs and returns a {@link Zone} from the current form values.
     *
     * <p>In {@link Mode#EDIT} the returned zone preserves the original zone's
     * {@code zoneId} and {@code eventId} so that {@code ZoneService.updateZone()}
     * can identify the record to update.
     *
     * @return the populated {@link Zone}; never {@code null}
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
