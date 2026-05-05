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
 * Punto de entrada de la aplicación JavaFX para TicketSync.
 *
 * <p>Secuencia de inicio:
 * <ol>
 *   <li>Inicializar directorios de la aplicación (logs, tickets) mediante {@link FilePathUtil}</li>
 *   <li>Verificar la conexión a la base de datos; mostrar un diálogo de error y salir si falla</li>
 *   <li>Iniciar el planificador en segundo plano {@link DatabaseHealthMonitor}</li>
 *   <li>Cargar y mostrar la escena de inicio de sesión</li>
 * </ol>
 *
 * <p>Secuencia de apagado (vía {@link #stop()}): detiene el monitor de salud y
 * cierra el pool de conexiones HikariCP antes de que la JVM finalice.
 *

 * @since 1.0
 */
public class App extends Application {

    /** Crea una nueva instancia de {@code App} (invocada por el entorno de ejecución JavaFX vía reflexión). */
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
        // Instalar AtlantaFX antes de mostrar cualquier Alert/Dialog para que los errores de inicio también tengan tema.
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        if (!ensureApplicationDirectories()) {
            return;
        }
        logStartupInformation();

        // Verificar si falta TICKETSYNC_MASTER_KEY antes de que se dispare la inicialización de clases
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
            DatabaseConfig.migrateDatabase();
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
        } catch (IllegalStateException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            DialogThemeHelper.apply(alert);
            alert.setTitle("Database Migration Error");
            alert.setHeaderText("Could not initialise database schema");
            alert.setContentText("Flyway migration failed: " + e.getMessage());
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
     * Reemplaza el nodo raíz de la escena principal de la aplicación.
     *
     * @param fxml nombre del archivo FXML (sin extensión) relativo a la ruta de recursos com/ticketsync
     * @throws IOException si el recurso FXML no se puede encontrar o cargar
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
     * Punto de entrada de la aplicación — delega a {@link Application#launch(String...)}.
     *
     * @param args argumentos de línea de comandos (actualmente no utilizados)
     */
    public static void main(String[] args) {
        launch();
    }

}
