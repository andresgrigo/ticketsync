package com.ticketsync;

import atlantafx.base.theme.PrimerLight;
import com.ticketsync.util.DatabaseConfig;
import com.ticketsync.util.DatabaseHealthMonitor;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URL;

import java.io.IOException;
import javafx.scene.control.Alert;

/**
 * JavaFX App
 */
public class App extends Application {

    private static final Logger LOGGER = LogManager.getLogger(App.class);
    private static Scene scene;
    private volatile boolean dbStarted = false;

    @Override
    public void start(Stage stage) throws IOException {
        logStartupInformation();

        // Check for missing TICKETSYNC_MASTER_KEY before class initialization fires
        try {
            if (!DatabaseConfig.testConnection()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Database Connection Error");
                alert.setHeaderText("Cannot connect to the database");
                alert.setContentText("Failed to reach the database. Ensure PostgreSQL is running on localhost:5432.");
                alert.showAndWait();
                Platform.exit();
                return;
            }
        } catch (ExceptionInInitializerError e) {
            Throwable cause = e.getCause();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (cause instanceof IllegalStateException && cause.getMessage() != null
                    && cause.getMessage().contains("TICKETSYNC_MASTER_KEY")) {
                alert.setTitle("Missing Environment Variable");
                alert.setHeaderText("Missing required environment variable: TICKETSYNC_MASTER_KEY");
                alert.setContentText(
                        "Missing required environment variable: TICKETSYNC_MASTER_KEY. Set this variable before starting the application.");
            } else {
                alert.setTitle("Database Connection Error");
                alert.setHeaderText("Cannot connect to the database");
                alert.setContentText("Failed to reach the database. Ensure PostgreSQL is running on localhost:5432.");
            }
            alert.showAndWait();
            Platform.exit();
            return;
        }

        DatabaseHealthMonitor.getInstance().start();
        dbStarted = true;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        scene = new Scene(loadFXML("LoginView"), 640, 480);
        stage.setTitle("TicketSync");
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
            if (dbStarted) {
                DatabaseHealthMonitor.getInstance().shutdown();
                DatabaseConfig.shutdown();
            }
        } finally {
            super.stop();
        }
    }

    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        URL resource = App.class.getResource(fxml + ".fxml");
        if (resource == null) {
            throw new IOException("FXML resource not found: " + fxml + ".fxml");
        }
        FXMLLoader fxmlLoader = new FXMLLoader(resource);
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

}