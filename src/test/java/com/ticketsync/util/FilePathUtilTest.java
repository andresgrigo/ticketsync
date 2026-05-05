package com.ticketsync.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePathUtilTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void restoreSystemProperties() {
        System.clearProperty(FilePathUtil.TICKETS_DIRECTORY_PROPERTY);
        System.clearProperty(FilePathUtil.CONFIG_DIRECTORY_PROPERTY);
        System.clearProperty(FilePathUtil.LOG_FILE_PROPERTY);
        System.clearProperty(FilePathUtil.LOG_PATTERN_PROPERTY);
    }

    @Test
    void resolveLogsAndConfigDirectories_useNormalizedPathComposition() {
        Path userHome = tempDir.resolve("users").resolve("andres");

        Path appHome = FilePathUtil.resolveAppHomeDirectory(userHome, System.getProperty("os.name", ""));
        Path logsDirectory = FilePathUtil.resolveLogsDirectory(appHome);
        Path configDirectory = FilePathUtil.resolveConfigDirectory(userHome);

        if (FilePathUtil.isWindows(System.getProperty("os.name", ""))) {
            assertEquals(Path.of("C:\\TicketSync"), appHome);
        } else {
            assertEquals(userHome.toAbsolutePath().normalize().resolve("ticketsync"), appHome);
        }

        assertEquals(appHome.resolve("logs"), logsDirectory);
        assertEquals(userHome.toAbsolutePath().normalize().resolve(".ticketsync").resolve("config"), configDirectory);
    }

    @Test
    void getTicketsDirectory_usesConfiguredPropertyWhenPresent() {
        Path configuredTicketsDirectory = tempDir.resolve("runtime").resolve("..").resolve("saved-tickets");
        System.setProperty(FilePathUtil.TICKETS_DIRECTORY_PROPERTY, configuredTicketsDirectory.toString());

        Path ticketsDirectory = FilePathUtil.getTicketsDirectory();

        assertEquals(configuredTicketsDirectory.toAbsolutePath().normalize(), ticketsDirectory);
    }

    @Test
    void ensureDirectoryExists_createsNestedDirectories() throws IOException {
        Path nestedDirectory = tempDir.resolve("logs").resolve("2026").resolve("04");

        Path ensuredDirectory = FilePathUtil.ensureDirectoryExists(nestedDirectory);

        assertEquals(nestedDirectory.toAbsolutePath().normalize(), ensuredDirectory);
        assertTrue(Files.isDirectory(ensuredDirectory));
    }

    @Test
    void initializeRuntimeProperties_setsResolvedLogPaths() {
        FilePathUtil.initializeRuntimeProperties();

        Path logsDirectory = FilePathUtil.getLogsDirectory();
        assertEquals(FilePathUtil.getConfigDirectory().toString(),
                System.getProperty(FilePathUtil.CONFIG_DIRECTORY_PROPERTY));
        assertEquals(logsDirectory.resolve("ticketsync.log").toString(),
                System.getProperty(FilePathUtil.LOG_FILE_PROPERTY));
        assertEquals(logsDirectory.resolve("ticketsync-%d{yyyy-MM-dd}-%i.log.gz").toString(),
                System.getProperty(FilePathUtil.LOG_PATTERN_PROPERTY));
    }
}
