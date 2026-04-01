package com.ticketsync;

import com.ticketsync.util.DatabaseConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import javafx.scene.control.Alert;
/**
 * JavaFX App
 */
public class App extends Application {

    private static final Logger LOGGER = LogManager.getLogger(App.class);
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        logStartupInformation();
        
        // Validate database connectivity and authentication on startup
        if (!DatabaseConfig.testConnection()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Connection Error");
            alert.setHeaderText("Cannot connect to the database");
            alert.setContentText("Failed to reach the database. Ensure PostgreSQL is running on localhost:5432.");
            alert.showAndWait();
            Platform.exit();
            return;
        }
        
        scene = new Scene(loadFXML("primary"), 640, 480);
        stage.setScene(scene);
        stage.show();
    }
    
    private void logStartupInformation() {
        LOGGER.info("=================================================");
        LOGGER.info("TicketSync Application Starting");
        LOGGER.info("Version: 1.0-SNAPSHOT");
        LOGGER.info("Java Version: {}", System.getProperty("java.version", "unknown"));
        LOGGER.info("Java Vendor: {}", System.getProperty("java.vendor", "unknown"));
        LOGGER.info("OS Name: {}", System.getProperty("os.name", "unknown"));
        LOGGER.info("OS Version: {}", System.getProperty("os.version", "unknown"));
        LOGGER.info("OS Architecture: {}", System.getProperty("os.arch", "unknown"));
        LOGGER.info("User Directory: {}", System.getProperty("user.dir", "unknown"));
        LOGGER.info("=================================================");
    }
    
    @Override
    public void stop() throws Exception {
        try {
            LOGGER.info("TicketSync Application Shutting Down");
        } finally {
            super.stop();
        }
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