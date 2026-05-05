package com.ticketsync;

import java.io.IOException;
import javafx.fxml.FXML;

/**
 * Controlador mínimo para la vista secundaria en el flujo de navegación demo de TicketSync.
 *
 * <p>Su única responsabilidad es volver a la vista FXML primaria
 * cuando el usuario activa la acción de retroceso.
 */
public class SecondaryController {

    /** Crea un nuevo {@code SecondaryController} (invocado por FXMLLoader vía reflexión). */
    public SecondaryController() {
    }

    @FXML
    private void switchToPrimary() throws IOException {
        App.setRoot("primary");
    }
}