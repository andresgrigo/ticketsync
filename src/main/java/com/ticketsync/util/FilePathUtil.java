package com.ticketsync.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Manejo centralizado de rutas del sistema de archivos para directorios en tiempo de ejecución.
 *
 * <p>Usa {@link Path#of(String, String...)} de forma consistente; en Java 21 esta es la
 * misma estrategia de resolución del sistema de archivos que {@code Paths.get(...)}.
 */
public final class FilePathUtil {

    /** Clave de propiedad del sistema para sobreescribir el directorio de salida de tickets. */
    public static final String TICKETS_DIRECTORY_PROPERTY = "ticketsync.tickets.dir";
    static final String CONFIG_DIRECTORY_PROPERTY = "ticketsync.config.dir";
    static final String LOG_FILE_PROPERTY = "ticketsync.log.file";
    static final String LOG_PATTERN_PROPERTY = "ticketsync.log.pattern";

    private FilePathUtil() {
    }

    /**
     * Devuelve el directorio home del usuario actual.
     *
     * @return ruta absoluta y normalizada al directorio home del usuario
     */
    public static Path getUserHomeDirectory() {
        return normalize(Path.of(System.getProperty("user.home", ".")));
    }

    /**
     * Devuelve el directorio home de la aplicación para el sistema operativo actual.
     *
     * <p>En Windows es {@code C:\TicketSync}; en otras plataformas es
     * {@code ~/ticketsync}.
     *
     * @return ruta absoluta y normalizada al directorio home de la aplicación
     */
    public static Path getAppHomeDirectory() {
        return resolveAppHomeDirectory(getUserHomeDirectory(), System.getProperty("os.name", ""));
    }

    /**
     * Devuelve el directorio de salida de logs de la aplicación.
     *
     * @return ruta absoluta y normalizada al directorio de logs
     */
    public static Path getLogsDirectory() {
        return resolveLogsDirectory(getAppHomeDirectory());
    }

    /**
     * Devuelve el directorio de configuración de la aplicación.
     *
     * @return ruta absoluta y normalizada a {@code ~/.ticketsync/config}
     */
    public static Path getConfigDirectory() {
        return resolveConfigDirectory(getUserHomeDirectory());
    }

    /**
     * Devuelve la ruta al archivo de propiedades JDBC.
     *
     * @return ruta absoluta y normalizada a {@code ~/.ticketsync/config/jdbc.properties}
     */
    public static Path getJdbcPropertiesPath() {
        return getConfigDirectory().resolve("jdbc.properties").normalize();
    }

    /**
     * Devuelve el directorio de salida de tickets.
     *
     * <p>Recurre a {@code ./tickets} relativo al directorio de trabajo cuando la
     * propiedad del sistema {@value #TICKETS_DIRECTORY_PROPERTY} no está configurada.
     *
     * @return ruta absoluta y normalizada al directorio de tickets
     */
    public static Path getTicketsDirectory() {
        return resolveTicketsDirectory(System.getProperty(TICKETS_DIRECTORY_PROPERTY));
    }

    /**
     * Crea todos los directorios intermedios para la ruta dada, si no existen.
     *
     * @param path la ruta del directorio a crear; no debe ser {@code null}
     * @return la ruta normalizada y absoluta que fue creada o ya existía
     * @throws IOException si los directorios no se pueden crear
     */
    public static Path ensureDirectoryExists(Path path) throws IOException {
        return Files.createDirectories(normalize(path));
    }

    /**
     * Inicializa las propiedades del sistema de ejecución de Log4j usadas por la configuración de logging.
     *
     * <p>Debe llamarse una vez antes de que ocurra cualquier registro. Lo llama el inicializador
     * estático de {@code App} para garantizar una ejecución temprana.
     */
    public static void initializeRuntimeProperties() {
        Path logsDirectory = getLogsDirectory();
        System.setProperty(CONFIG_DIRECTORY_PROPERTY, getConfigDirectory().toString());
        System.setProperty(LOG_FILE_PROPERTY, logsDirectory.resolve("ticketsync.log").toString());
        System.setProperty(LOG_PATTERN_PROPERTY, logsDirectory.resolve("ticketsync-%d{yyyy-MM-dd}-%i.log.gz").toString());
    }

    /**
     * Asegura que los directorios de ejecución de la aplicación (logs y configuración) existan,
     * creándolos si es necesario.
     *
     * @throws IOException si cualquier directorio no puede ser creado
     */
    public static void ensureApplicationDirectories() throws IOException {
        ensureDirectoryExists(getLogsDirectory());
        ensureDirectoryExists(getConfigDirectory());
    }

    static Path resolveAppHomeDirectory(Path userHomeDirectory, String osName) {
        Path normalizedUserHome = normalize(userHomeDirectory);
        if (isWindows(osName)) {
            return normalize(Path.of("C:\\TicketSync"));
        }
        return normalizedUserHome.resolve("ticketsync").normalize();
    }

    static Path resolveLogsDirectory(Path appHomeDirectory) {
        return normalize(appHomeDirectory).resolve("logs").normalize();
    }

    static Path resolveConfigDirectory(Path userHomeDirectory) {
        return normalize(userHomeDirectory).resolve(".ticketsync").resolve("config").normalize();
    }

    static Path resolveTicketsDirectory(String configuredTicketsDirectory) {
        if (configuredTicketsDirectory == null || configuredTicketsDirectory.isBlank()) {
            return normalize(Path.of(".", "tickets"));
        }
        return normalize(Path.of(configuredTicketsDirectory));
    }

    static boolean isWindows(String osName) {
        return Objects.requireNonNullElse(osName, "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path must not be null")
                .toAbsolutePath()
                .normalize();
    }
}
