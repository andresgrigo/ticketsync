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

    public static final String TICKETS_DIRECTORY_PROPERTY = "ticketsync.tickets.dir";
    static final String CONFIG_DIRECTORY_PROPERTY = "ticketsync.config.dir";
    static final String LOG_FILE_PROPERTY = "ticketsync.log.file";
    static final String LOG_PATTERN_PROPERTY = "ticketsync.log.pattern";

    private FilePathUtil() {
    }

    public static Path getUserHomeDirectory() {
        return normalize(Path.of(System.getProperty("user.home", ".")));
    }

    public static Path getAppHomeDirectory() {
        return resolveAppHomeDirectory(getUserHomeDirectory(), System.getProperty("os.name", ""));
    }

    public static Path getLogsDirectory() {
        return resolveLogsDirectory(getAppHomeDirectory());
    }

    public static Path getConfigDirectory() {
        return resolveConfigDirectory(getUserHomeDirectory());
    }

    public static Path getJdbcPropertiesPath() {
        return getConfigDirectory().resolve("jdbc.properties").normalize();
    }

    public static Path getTicketsDirectory() {
        return resolveTicketsDirectory(System.getProperty(TICKETS_DIRECTORY_PROPERTY));
    }

    public static Path ensureDirectoryExists(Path path) throws IOException {
        return Files.createDirectories(normalize(path));
    }

    public static void initializeRuntimeProperties() {
        Path logsDirectory = getLogsDirectory();
        System.setProperty(CONFIG_DIRECTORY_PROPERTY, getConfigDirectory().toString());
        System.setProperty(LOG_FILE_PROPERTY, logsDirectory.resolve("ticketsync.log").toString());
        System.setProperty(LOG_PATTERN_PROPERTY, logsDirectory.resolve("ticketsync-%d{yyyy-MM-dd}-%i.log.gz").toString());
    }

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
