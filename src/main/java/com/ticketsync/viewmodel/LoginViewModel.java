package com.ticketsync.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Presentation-layer state for the login screen.
 *
 * <p>Holds observable JavaFX properties for username, password, in-progress
 * state, and error message. The {@link com.ticketsync.controller.LoginController}
 * binds UI controls to these properties so that all state changes are
 * automatically reflected in the view.
 *
 * <p>This class has no reference to JavaFX UI controls and therefore can be
 * tested without initialising the JavaFX toolkit.
 */
public class LoginViewModel {

    private final StringProperty username = new SimpleStringProperty("");
    private final StringProperty password = new SimpleStringProperty("");
    private final BooleanProperty loginInProgress = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");

    /**
     * Returns the observable username property.
     *
     * @return username property bound bidirectionally to the username field
     */
    public StringProperty usernameProperty() {
        return username;
    }

    /**
     * Returns the observable password property.
     *
     * @return password property bound bidirectionally to the password field
     */
    public StringProperty passwordProperty() {
        return password;
    }

    /**
     * Returns the observable login-in-progress property.
     *
     * <p>When {@code true}, the login button and credential fields are disabled
     * to prevent double-submission.
     *
     * @return loginInProgress property
     */
    public BooleanProperty loginInProgressProperty() {
        return loginInProgress;
    }

    /**
     * Returns the observable error message property.
     *
     * <p>When non-empty, the error label in the view becomes visible and
     * displays this text.
     *
     * @return errorMessage property
     */
    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    /**
     * Clears the current error message.
     *
     * <p>Hides the error label in the view by setting the message to an
     * empty string.
     */
    public void resetError() {
        errorMessage.set("");
    }
}
