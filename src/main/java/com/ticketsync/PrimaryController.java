package com.ticketsync;

import java.io.IOException;
import javafx.fxml.FXML;

/**
 * Secondary FXML controller used during initial application navigation.
 *
 * <p>Provides a navigation action to switch from the secondary view back to
 * the primary view via {@link App#setRoot(String)}.
 */
public class PrimaryController {

    /** Creates a new {@code PrimaryController} instance (invoked by FXMLLoader via reflection). */
    public PrimaryController() {
    }

    @FXML
    private void switchToSecondary() throws IOException {
        App.setRoot("secondary");
    }
}
