package com.ticketsync;

import java.io.IOException;
import javafx.fxml.FXML;

/**
 * Controlador FXML secundario utilizado durante la navegación inicial de la aplicación.
 *
 * <p>Proporciona una acción de navegación para cambiar desde la vista secundaria
 * de vuelta a la vista primaria mediante {@link App#setRoot(String)}.
 */
public class PrimaryController {

    /** Crea una nueva instancia de {@code PrimaryController} (invocada por FXMLLoader vía reflexión). */
    public PrimaryController() {
    }

    @FXML
    private void switchToSecondary() throws IOException {
        App.setRoot("secondary");
    }
}
