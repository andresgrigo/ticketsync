package com.ticketsync;

import java.io.IOException;
import javafx.fxml.FXML;

/**
 * Minimal controller for the secondary view in the TicketSync demo navigation flow.
 *
 * <p>Its only responsibility is switching back to the primary FXML view
 * when the user triggers the back action.
 */
public class SecondaryController {

    /** Creates a new {@code SecondaryController} (invoked by FXMLLoader via reflection). */
    public SecondaryController() {
    }

    @FXML
    private void switchToPrimary() throws IOException {
        App.setRoot("primary");
    }
}