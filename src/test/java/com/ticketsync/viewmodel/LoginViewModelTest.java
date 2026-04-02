package com.ticketsync.viewmodel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LoginViewModel}.
 *
 * <p>These tests cover pure presentation state and require no JavaFX
 * toolkit initialisation.
 */
class LoginViewModelTest {

    private LoginViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new LoginViewModel();
    }

    @Test
    void initialState_propertiesAreEmpty() {
        assertEquals("", viewModel.usernameProperty().get(), "username should start empty");
        assertEquals("", viewModel.passwordProperty().get(), "password should start empty");
        assertEquals("", viewModel.errorMessageProperty().get(), "errorMessage should start empty");
        assertFalse(viewModel.loginInProgressProperty().get(), "loginInProgress should start false");
    }

    @Test
    void settersAndGetters_roundTrip() {
        viewModel.usernameProperty().set("testuser");
        viewModel.passwordProperty().set("secret");

        assertEquals("testuser", viewModel.usernameProperty().get());
        assertEquals("secret", viewModel.passwordProperty().get());
    }

    @Test
    void resetError_clearsErrorMessage() {
        viewModel.errorMessageProperty().set("Some error");
        assertEquals("Some error", viewModel.errorMessageProperty().get());

        viewModel.resetError();

        assertEquals("", viewModel.errorMessageProperty().get());
    }
}
