package com.ticketsync.controller;

import com.ticketsync.model.User;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * FXML controller for the user create/edit dialog ({@code UserFormView.fxml}).
 *
 * <p>Operates in two modes controlled by {@link Mode}:
 * <ul>
 *   <li>{@link Mode#CREATE} — all fields visible and editable; validates
 *       username, password length, password confirmation, and role.</li>
 *   <li>{@link Mode#EDIT} — username shown read-only, password fields hidden;
 *       validates only that a role is selected.</li>
 * </ul>
 *
 * <p>The dialog container (owned by {@code AdminDashboardController}) attaches
 * an event filter to the OK button that calls {@link #validate()} before
 * allowing the dialog to close, keeping all validation logic here.
 */
public class UserFormController {

    /** Operational mode that determines which fields are active and what is validated. */
    public enum Mode {
        /** All fields are active; both username and password must be supplied. */
        CREATE,
        /** Username is read-only; password fields are hidden; only role is editable. */
        EDIT
    }

    private static final List<String> ROLES = List.of("ADMIN", "VENDOR");

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label passwordLabel;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label confirmPasswordLabel;

    @FXML
    private ComboBox<String> roleComboBox;

    @FXML
    private Label errorLabel;

    private Mode mode = Mode.CREATE;
    private User existingUser = null;

    /**
     * Initialises the controller after FXML injection.
     *
     * <p>Populates the role combo-box with the accepted role values and sets
     * a placeholder prompt.
     */
    @FXML
    public void initialize() {
        roleComboBox.getItems().addAll(ROLES);
        roleComboBox.setPromptText("Select role");
    }

    /**
     * Sets the operational mode for this form.
     *
     * <p>In {@link Mode#EDIT} the username field is made read-only and both
     * password rows are hidden so that the admin can change the role only.
     * In {@link Mode#CREATE} all fields are shown and editable (default state
     * after FXML initialisation).
     *
     * @param mode the mode to apply; must not be {@code null}
     */
    public void setMode(Mode mode) {
        this.mode = mode;
        if (mode == Mode.EDIT) {
            usernameField.setEditable(false);
            setPasswordRowVisible(false);
        } else {
            usernameField.setEditable(true);
            setPasswordRowVisible(true);
        }
    }

    /**
     * Pre-populates the form fields with the data from an existing user.
     *
     * <p>Should only be called in {@link Mode#EDIT} after {@link #setMode(Mode)}.
     *
     * @param user the user whose data should be displayed; must not be {@code null}
     */
    public void setUser(User user) {
        this.existingUser = user;
        usernameField.setText(user.getUsername());
        roleComboBox.setValue(user.getRole());
    }

    /**
     * Validates the form contents according to the current mode.
     *
     * <p>On failure the error label is made visible with an explanatory message
     * and {@code false} is returned so the dialog container can suppress the
     * close event. On success the error label is hidden and {@code true} is
     * returned.
     *
     * @return {@code true} if all validations pass; {@code false} otherwise
     */
    public boolean validate() {
        if (mode == Mode.CREATE) {
            if (usernameField.getText() == null || usernameField.getText().isBlank()) {
                showError("Username is required");
                return false;
            }
            if (passwordField.getText() == null || passwordField.getText().length() < 8) {
                showError("Password must be at least 8 characters");
                return false;
            }
            if (!passwordField.getText().equals(confirmPasswordField.getText())) {
                showError("Passwords do not match");
                return false;
            }
        }
        if (roleComboBox.getValue() == null) {
            showError("Please select a role");
            return false;
        }
        hideError();
        return true;
    }

    /**
     * Returns the username entered in the form, trimmed of leading/trailing whitespace.
     *
     * @return the trimmed username string
     */
    public String getUsername() {
        return usernameField.getText().trim();
    }

    /**
     * Returns the password entered in the password field.
     *
     * <p>In {@link Mode#EDIT} the password fields are hidden; this method
     * returns an empty string in that case.
     *
     * @return the password string, or {@code ""} in EDIT mode
     */
    public String getPassword() {
        if (mode == Mode.EDIT) {
            return "";
        }
        return passwordField.getText();
    }

    /**
     * Returns the role currently selected in the combo-box.
     *
     * @return the selected role string, or {@code null} if none is selected
     */
    public String getSelectedRole() {
        return roleComboBox.getValue();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void setPasswordRowVisible(boolean visible) {
        setNodeVisible(passwordLabel, visible);
        setNodeVisible(passwordField, visible);
        setNodeVisible(confirmPasswordLabel, visible);
        setNodeVisible(confirmPasswordField, visible);
    }

    private void setNodeVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * Displays a validation error sourced externally (e.g. from a database
     * uniqueness check performed by the calling controller).
     *
     * @param message the error message to display; must not be {@code null}
     */
    public void showExternalError(String message) {
        showError(message);
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
