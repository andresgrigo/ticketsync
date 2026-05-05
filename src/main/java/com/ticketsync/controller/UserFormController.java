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
 * Controlador FXML para el diálogo de creación/edición de usuario ({@code UserFormView.fxml}).
 *
 * <p>Opera en dos modos controlados por {@link Mode}:
 * <ul>
 *   <li>{@link Mode#CREATE} — todos los campos visibles y editables; valida
 *       nombre de usuario, longitud de contraseña, confirmación de contraseña y rol.</li>
 *   <li>{@link Mode#EDIT} — nombre de usuario mostrado como solo lectura, campos de contraseña ocultos;
 *       valida únicamente que se haya seleccionado un rol.</li>
 * </ul>
 *
 * <p>El contenedor del diálogo (propiedad de {@code AdminDashboardController}) adjunta
 * un filtro de eventos al botón Aceptar que llama a {@link #validate()} antes de
 * permitir que el diálogo se cierre, manteniendo toda la lógica de validación aquí.
 */
public class UserFormController {

    /** Crea un nuevo UserFormController; instanciado por FXMLLoader. */
    public UserFormController() { }

    /** Modo operacional que determina qué campos están activos y qué se valida. */
    public enum Mode {
        /** Todos los campos están activos; tanto el nombre de usuario como la contraseña deben proporcionarse. */
        CREATE,
        /** El nombre de usuario es de solo lectura; los campos de contraseña están ocultos; solo el rol es editable. */
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
    

    /**
     * Inicializa el controlador después de la inyección FXML.
     *
     * <p>Rellena el combo-box de roles con los valores de rol aceptados y establece
     * un prompt de marcador de posición.
     */
    @FXML
    public void initialize() {
        roleComboBox.getItems().addAll(ROLES);
        roleComboBox.setPromptText("Select role");
    }

    /**
     * Establece el modo operacional para este formulario.
     *
     * <p>En {@link Mode#EDIT} el campo de nombre de usuario se establece como solo lectura y ambas
     * filas de contraseña se ocultan para que el administrador solo pueda cambiar el rol.
     * En {@link Mode#CREATE} todos los campos se muestran y son editables (estado predeterminado
     * después de la inicialización FXML).
     *
     * @param mode el modo a aplicar; no debe ser {@code null}
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
     * Rellena previamente los campos del formulario con los datos de un usuario existente.
     *
     * <p>Solo debe llamarse en {@link Mode#EDIT} después de {@link #setMode(Mode)}.
     *
     * @param user el usuario cuyos datos deben mostrarse; no debe ser {@code null}
     */
    public void setUser(User user) {
        usernameField.setText(user.getUsername());
        roleComboBox.setValue(user.getRole());
    }

    /**
     * Valida el contenido del formulario según el modo actual.
     *
     * <p>En caso de fallo, la etiqueta de error se hace visible con un mensaje explicativo
     * y se retorna {@code false} para que el contenedor del diálogo pueda suprimir el
     * evento de cierre. En caso de éxito, la etiqueta de error se oculta y se retorna {@code true}.
     *
     * @return {@code true} si todas las validaciones pasan; {@code false} en caso contrario
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
     * Retorna el nombre de usuario ingresado en el formulario, recortado de espacios en blanco al inicio/final.
     *
     * @return la cadena de nombre de usuario recortada
     */
    public String getUsername() {
        return usernameField.getText().trim();
    }

    /**
     * Retorna la contraseña ingresada en el campo de contraseña.
     *
     * <p>En {@link Mode#EDIT} los campos de contraseña están ocultos; este método
     * retorna una cadena vacía en ese caso.
     *
     * @return la cadena de contraseña, o {@code ""} en modo EDIT
     */
    public String getPassword() {
        if (mode == Mode.EDIT) {
            return "";
        }
        return passwordField.getText();
    }

    /**
     * Retorna el rol actualmente seleccionado en el combo-box.
     *
     * @return la cadena del rol seleccionado, o {@code null} si no hay ninguno seleccionado
     */
    public String getSelectedRole() {
        return roleComboBox.getValue();
    }

    // -------------------------------------------------------------------------
    // Métodos privados auxiliares
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
     * Muestra un error de validación proveniente de forma externa (por ejemplo, de una
     * comprobación de unicidad en la base de datos realizada por el controlador llamante).
     *
     * @param message el mensaje de error a mostrar; no debe ser {@code null}
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
