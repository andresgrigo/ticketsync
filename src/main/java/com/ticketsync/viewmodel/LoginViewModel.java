package com.ticketsync.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Estado de la capa de presentación para la pantalla de inicio de sesión.
 *
 * <p>Mantiene propiedades JavaFX observables para el nombre de usuario, la contraseña,
 * el estado en progreso y el mensaje de error. El {@link com.ticketsync.controller.LoginController}
 * enlaza los controles de la UI a estas propiedades para que todos los cambios de estado
 * se reflejen automáticamente en la vista.
 *
 * <p>Esta clase no tiene referencia a controles de la UI de JavaFX y por tanto puede ser
 * probada sin inicializar el toolkit de JavaFX.
 */
public class LoginViewModel {

    /** Crea un nuevo {@code LoginViewModel} con valores de propiedad vacíos. */
    public LoginViewModel() {
    }

    private final StringProperty username= new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final BooleanProperty loginInProgress = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /**
     * Retorna la propiedad observable del nombre de usuario.
     *
     * @return propiedad de nombre de usuario enlazada bidireccionalmente al campo de nombre de usuario
     */
    public StringProperty usernameProperty() {
        return username;
    }

    /**
     * Retorna la propiedad observable de la contraseña.
     *
     * @return propiedad de contraseña enlazada bidireccionalmente al campo de contraseña
     */
    public StringProperty passwordProperty() {
        return password;
    }

    /**
     * Retorna la propiedad observable de inicio de sesión en progreso.
     *
     * <p>Cuando es {@code true}, el botón de inicio de sesión y los campos de credenciales
     * se deshabilitan para prevenir el doble envío.
     *
     * @return propiedad loginInProgress
     */
    public BooleanProperty loginInProgressProperty() {
        return loginInProgress;
    }

    /**
     * Retorna la propiedad observable del mensaje de error.
     *
     * <p>Cuando no está vacía, la etiqueta de error en la vista se hace visible
     * y muestra este texto.
     *
     * @return propiedad errorMessage
     */
    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /**
     * Limpia el mensaje de error actual.
     *
     * <p>Oculta la etiqueta de error en la vista estableciendo el mensaje como
     * cadena vacía.
     */
    public void resetError() {
        errorMessage.set("");
    }
}
