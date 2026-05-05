package com.ticketsync.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

final class ContainerBackedPostgresHarness {

    private static final String MIGRATION_PATH_PREFIX = "db/migration/";
    private static final List<String> MIGRATION_FILENAMES = List.of(
            "V001__create_users_table.sql",
            "V002__create_events_table.sql",
            "V003__create_zones_seats_tables.sql",
            "V004__create_sales_audit_tables.sql",
            "V005__create_seat_notification_trigger.sql"
    );

    private ContainerBackedPostgresHarness() {
    }

    static PostgreSQLContainer<?> createPostgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("ticketsync")
                .withUsername("ticketsync")
                .withPassword("ticketsync");
    }

    static ConnectionFactory createConnectionFactory(PostgreSQLContainer<?> postgres) {
        return () -> DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
    }

    static Flyway createFlyway(PostgreSQLContainer<?> postgres) {
        return Flyway.configure(ContainerBackedPostgresHarness.class.getClassLoader())
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .resourceProvider(new ClasspathMigrationResourceProvider())
                .load();
    }

    static HikariDataSource createDataSource(PostgreSQLContainer<?> postgres,
                                             String poolName,
                                             int maximumPoolSize,
                                             int minimumIdle,
                                             long connectionTimeoutMillis) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeoutMillis);
        config.setIdleTimeout(Duration.ofMinutes(5).toMillis());
        config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName(poolName);
        return new HikariDataSource(config);
    }

    private static final class ClasspathMigrationResourceProvider implements ResourceProvider {

        @Override
        public LoadableResource getResource(String name) {
            String filename = resolveFilename(name);
            return MIGRATION_FILENAMES.contains(filename)
                    ? new ClasspathLoadableMigrationResource(MIGRATION_PATH_PREFIX + filename)
                    : null;
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return MIGRATION_FILENAMES.stream()
                    .filter(filename -> filename.startsWith(prefix))
                    .filter(filename -> hasMatchingSuffix(filename, suffixes))
                    .map(filename -> new ClasspathLoadableMigrationResource(MIGRATION_PATH_PREFIX + filename))
                    .map(LoadableResource.class::cast)
                    .toList();
        }

        private static String resolveFilename(String name) {
            int lastSlash = name.lastIndexOf('/');
            return lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
        }

        private static boolean hasMatchingSuffix(String path, String[] suffixes) {
            for (String suffix : suffixes) {
                if (path.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ClasspathLoadableMigrationResource extends LoadableResource {
        private final String path;

        private ClasspathLoadableMigrationResource(String path) {
            this.path = path;
        }

        @Override
        public Reader read() {
            InputStream stream = openResourceStream(path);
            if (stream == null) {
                throw new IllegalStateException("Missing migration resource: " + path);
            }
            return new InputStreamReader(stream, StandardCharsets.UTF_8);
        }

        @Override
        public String getAbsolutePath() {
            return path;
        }

        @Override
        public String getAbsolutePathOnDisk() {
            return path;
        }

        @Override
        public String getFilename() {
            int lastSlash = path.lastIndexOf('/');
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        }

        @Override
        public String getRelativePath() {
            return path;
        }

        private static InputStream openResourceStream(String path) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                InputStream stream = contextClassLoader.getResourceAsStream(path);
                if (stream != null) {
                    return stream;
                }
            }

            ClassLoader classLoader = ContainerBackedPostgresHarness.class.getClassLoader();
            if (classLoader != null) {
                InputStream stream = classLoader.getResourceAsStream(path);
                if (stream != null) {
                    return stream;
                }
            }

            InputStream systemStream = ClassLoader.getSystemResourceAsStream(path);
            if (systemStream != null) {
                return systemStream;
            }

            try {
                Path sourcePath = Path.of("src", "main", "resources").resolve(path);
                if (Files.exists(sourcePath)) {
                    return Files.newInputStream(sourcePath);
                }

                Path compiledPath = Path.of("target", "classes").resolve(path);
                if (Files.exists(compiledPath)) {
                    return Files.newInputStream(compiledPath);
                }
            } catch (Exception ignored) {
                return null;
            }

            return null;
        }
    }
}
