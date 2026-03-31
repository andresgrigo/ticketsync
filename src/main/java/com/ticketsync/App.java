package com.ticketsync;

import com.ticketsync.util.SchemaVersionValidator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import javafx.scene.control.Alert;
/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        // Validate database schema version on startup
        try {
            SchemaVersionValidator.validateSchemaVersion();
        } catch (RuntimeException e) {
            // Show error dialog instead of crashing silently
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Connection Error");
            alert.setHeaderText("Failed to validate database schema");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            Platform.exit();
            return;
        }
        
        scene = new Scene(loadFXML("primary"), 640, 480);
        stage.setScene(scene);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

}