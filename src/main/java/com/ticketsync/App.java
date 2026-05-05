package com.ticketsync;

import atlantafx.base.theme.PrimerLight;
import com.ticketsync.util.AppTheme;
import com.ticketsync.util.DatabaseConfig;
import com.ticketsync.util.DatabaseHealthMonitor;
import com.ticketsync.util.DialogThemeHelper;
import com.ticketsync.util.FilePathUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import javafx.scene.control.Alert;

/**
 * JavaFX application entry point for TicketSync.
 *
 * <p>Startup sequence:
 * <ol>
 *   <li>Initialise application directories (logs, tickets) via {@link FilePathUtil}</li>
 *   <li>Test the database connection; show an error dialog and exit if it fails</li>
 *   <li>Start the {@link DatabaseHealthMonitor} background scheduler</li>
 *   <li>Load and display the Login scene</li>
 * </ol>
 *
 * <p>Shutdown sequence (via {@link #stop()}): stops the health monitor and
 * closes the HikariCP connection pool before the JVM exits.
 *

 * @since 1.0
 */
public class App extends Application {

    /** Creates a new {@code App} instance (invoked by the JavaFX runtime via reflection). */
    public App() {
    }

    static {
        FilePathUtil.initializeRuntimeProperties();
    }

    private static final Logger LOGGER = LogManager.getLogger(App.class);
    private static Scene scene;
    private volatile boolean dbStarted = false;

    @Override
    public void start(Stage stage) throws IOException {
        // Install AtlantaFX before any Alert/Dialog is shown so even early startup errors are themed.
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        if (!ensureApplicationDirectories()) {
            return;
        }
        logStartupInformation();

        // Check for missing TICKETSYNC_MASTER_KEY before class initialization fires
        try {
            if (!DatabaseConfig.testConnection()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                DialogThemeHelper.apply(alert);
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
            DialogThemeHelper.apply(alert);
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
        scene = new Scene(loadFXML("LoginView"), 640, 480);
        AppTheme.apply(scene);
        stage.setTitle("TicketSync");
        stage.setScene(scene);
        stage.show();
    }

    private boolean ensureApplicationDirectories() {
        try {
            FilePathUtil.ensureApplicationDirectories();
            return true;
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            DialogThemeHelper.apply(alert);
            alert.setTitle("Application Directory Error");
            alert.setHeaderText("Cannot prepare TicketSync directories");
            alert.setContentText("TicketSync could not prepare its logs/config directories. Check filesystem permissions and try again.");
            alert.showAndWait();
            Platform.exit();
            return false;
        }
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

    /**
     * Replaces the root node of the main application scene.
     *
     * @param fxml FXML file name (without extension) relative to the com/ticketsync resource path
     * @throws IOException if the FXML resource cannot be found or loaded
     */
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

    /**
     * Application entry point — delegates to {@link Application#launch(String...)}.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        launch();
    }

}
