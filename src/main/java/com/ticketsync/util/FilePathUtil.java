package com.ticketsync.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Centralized filesystem path handling for runtime directories.
 *
 * <p>Uses {@link Path#of(String, String...)} consistently; on Java 21 this is the
 * same filesystem resolution strategy as {@code Paths.get(...)}.
 */
public final class FilePathUtil {

    /** System property key for overriding the tickets output directory. */
    public static final String TICKETS_DIRECTORY_PROPERTY = "ticketsync.tickets.dir";
    static final String CONFIG_DIRECTORY_PROPERTY = "ticketsync.config.dir";
    static final String LOG_FILE_PROPERTY = "ticketsync.log.file";
    static final String LOG_PATTERN_PROPERTY = "ticketsync.log.pattern";

    private FilePathUtil() {
    }

    /**
     * Returns the current user's home directory.
     *
     * @return absolute, normalised path to the user's home directory
     */
    public static Path getUserHomeDirectory() {
        return normalize(Path.of(System.getProperty("user.home", ".")));
    }

    /**
     * Returns the application's home directory for the current OS.
     *
     * <p>On Windows this is {@code C:\TicketSync}; on other platforms it is
     * {@code ~/ticketsync}.
     *
     * @return absolute, normalised path to the application home directory
     */
    public static Path getAppHomeDirectory() {
        return resolveAppHomeDirectory(getUserHomeDirectory(), System.getProperty("os.name", ""));
    }

    /**
     * Returns the application's log output directory.
     *
     * @return absolute, normalised path to the logs directory
     */
    public static Path getLogsDirectory() {
        return resolveLogsDirectory(getAppHomeDirectory());
    }

    /**
     * Returns the application's configuration directory.
     *
     * @return absolute, normalised path to {@code ~/.ticketsync/config}
     */
    public static Path getConfigDirectory() {
        return resolveConfigDirectory(getUserHomeDirectory());
    }

    /**
     * Returns the path to the JDBC properties file.
     *
     * @return absolute, normalised path to {@code ~/.ticketsync/config/jdbc.properties}
     */
    public static Path getJdbcPropertiesPath() {
        return getConfigDirectory().resolve("jdbc.properties").normalize();
    }

    /**
     * Returns the tickets output directory.
     *
     * <p>Falls back to {@code ./tickets} relative to the working directory when the
     * {@value #TICKETS_DIRECTORY_PROPERTY} system property is not set.
     *
     * @return absolute, normalised path to the tickets directory
     */
    public static Path getTicketsDirectory() {
        return resolveTicketsDirectory(System.getProperty(TICKETS_DIRECTORY_PROPERTY));
    }

    /**
     * Creates all intermediate directories for the given path, if they do not already exist.
     *
     * @param path the directory path to create; must not be {@code null}
     * @return the normalised, absolute path that was created or already existed
     * @throws IOException if the directories cannot be created
     */
    public static Path ensureDirectoryExists(Path path) throws IOException {
        return Files.createDirectories(normalize(path));
    }

    /**
     * Initialises the Log4j runtime system properties used by the logging configuration.
     *
     * <p>Must be called once before any logging occurs. Called by {@code App}'s
     * static initialiser to guarantee early execution.
     */
    public static void initializeRuntimeProperties() {
        Path logsDirectory = getLogsDirectory();
        System.setProperty(CONFIG_DIRECTORY_PROPERTY, getConfigDirectory().toString());
        System.setProperty(LOG_FILE_PROPERTY, logsDirectory.resolve("ticketsync.log").toString());
        System.setProperty(LOG_PATTERN_PROPERTY, logsDirectory.resolve("ticketsync-%d{yyyy-MM-dd}-%i.log.gz").toString());
    }

    /**
     * Ensures the application runtime directories (logs and config) exist, creating them if necessary.
     *
     * @throws IOException if any directory cannot be created
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
