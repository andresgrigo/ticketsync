package com.ticketsync.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.jasypt.properties.EncryptableProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConfig proporciona un pool de conexiones singleton usando HikariCP.
 * 
 * <p>Esta clase gestiona un pool de hasta 5 conexiones de base de datos para operaciones
 * concurrentes dentro de una sola instancia de la aplicación (cabina o cliente administrador).
 * El pool se inicializa una vez cuando la clase se carga por primera vez y permanece
 * activo durante toda la vida de la aplicación.</p>
 * 
 * <p><strong>Configuración del Pool:</strong></p>
 * <ul>
 *   <li>Tamaño Máximo del Pool: 5 conexiones (por instancia de aplicación)</li>
 *   <li>Mínimo Inactivo: 2 conexiones (mantenidas activas)</li>
 *   <li>Tiempo de Espera de Conexión: 10 segundos</li>
 *   <li>Tiempo de Espera Inactivo: 5 minutos</li>
 *   <li>Vida Máxima: 30 minutos</li>
 * </ul>
 * 
 * <p><strong>Patrón de Uso:</strong></p>
 * <pre>{@code
 * Connection conn = null;
 * try {
 *     conn = DatabaseConfig.getConnection();
 *     // Usar conexión
 * } catch (SQLException e) {
 *     // Manejar error
 * } finally {
 *     if (conn != null) {
 *         conn.close(); // Devuelve la conexión al pool
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>Nota:</strong> Connection.close() devuelve la conexión al pool
 * en lugar de cerrar la conexión PostgreSQL subyacente.</p>
 * 

 * @version 1.0
 * @since 1.0
 */
public final class DatabaseConfig {
    
    private static final Logger LOGGER = LogManager.getLogger(DatabaseConfig.class);
    private static final HikariDataSource dataSource;
    
    // Inicializador estático - se ejecuta una vez cuando se carga la clase
    static {
        try {
            // Paso 1 — Leer y validar la clave maestra
            String masterKey = System.getenv("TICKETSYNC_MASTER_KEY");
            if (masterKey == null || masterKey.isBlank()) {
                LOGGER.error("La variable de entorno TICKETSYNC_MASTER_KEY no está configurada — no se puede iniciar la aplicación");
                throw new IllegalStateException("TICKETSYNC_MASTER_KEY environment variable is not set");
            }

            // Paso 2 — Crear el encriptador y cargar EncryptableProperties
            EncryptableProperties props = loadJdbcProperties(masterKey);

            // Paso 3 — Configurar HikariCP desde las propiedades descifradas
            String jdbcUrl = props.getProperty("jdbc.url");
            String jdbcUsername = props.getProperty("jdbc.username");
            String jdbcPassword = props.getProperty("jdbc.password");
            if (jdbcUrl == null || jdbcUsername == null || jdbcPassword == null) {
                throw new IllegalStateException(
                        "jdbc.properties is missing required key(s): jdbc.url, jdbc.username, or jdbc.password");
            }
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(jdbcUsername);
            config.setPassword(jdbcPassword); // decrypted transparently by EncryptableProperties
            config.setDriverClassName("org.postgresql.Driver");

            // Tamaño del pool para aplicación de escritorio con operaciones concurrentes:
            // - 1 conexión persistente para PostgreSQL LISTEN/NOTIFY
            // - 1-2 conexiones para procesamiento de transacciones
            // - 1-2 conexiones para consultas concurrentes/verificaciones de salud
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(2);

            // Configuración de tiempos de espera
            config.setConnectionTimeout(10000);  // 10 segundos de espera máxima
            config.setIdleTimeout(300000);       // 5 minutos inactivo antes de liberarse
            config.setMaxLifetime(1800000);      // 30 minutos de vida máxima de conexión

            // Configuración de verificación de salud
            config.setConnectionTestQuery("SELECT 1");

            // Nombre del pool para registro de logs
            config.setPoolName("TicketSync-Pool");

            dataSource = new HikariDataSource(config);

            // Validar conectividad inmediatamente para fallar rápido si la base de datos es inalcanzable
            try (Connection testConn = dataSource.getConnection()) {
                LOGGER.info("HikariCP connection pool initialized successfully: {} max connections",
                          config.getMaximumPoolSize());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize HikariCP connection pool", e);
            throw new ExceptionInInitializerError(e);
        }
    }
    
    /**
     * Constructor privado que impide la instanciación de esta clase de utilidad.
     * 
     * @throws UnsupportedOperationException siempre, para prevenir la instanciación
     */
    private DatabaseConfig() {
        throw new UnsupportedOperationException("DatabaseConfig is a utility class and cannot be instantiated");
    }

    private static EncryptableProperties loadJdbcProperties(String masterKey) throws IOException {
        EncryptableProperties props = loadBundledJdbcProperties(masterKey);
        Path externalJdbcProperties = FilePathUtil.getJdbcPropertiesPath();
        if (Files.exists(externalJdbcProperties)) {
            byte[] configBytes = Files.readAllBytes(externalJdbcProperties);
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(configBytes);
                 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            LOGGER.info("Loaded database configuration defaults from classpath and overrides from {}", externalJdbcProperties);
        } else {
            LOGGER.info("Loaded database configuration from bundled classpath defaults");
        }
        return props;
    }

    private static EncryptableProperties loadBundledJdbcProperties(String masterKey) throws IOException {
        EncryptableProperties props = new EncryptableProperties(EncryptionUtil.createEncryptor(masterKey));
        try (InputStream inputStream = DatabaseConfig.class.getClassLoader().getResourceAsStream("jdbc.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("jdbc.properties not found on classpath");
            }
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }
        return props;
    }
    
    /**
     * Gets a database connection from the HikariCP connection pool.
     * 
     * <p>Connections are pooled and reused. When finished with a connection,
     * always call {@code Connection.close()} to return it to the pool.</p>
     * 
     * <p><strong>Performance:</strong> Connection checkout completes in under
     * 50ms under normal load.</p>
     * 
     * @return a valid database connection from the pool
     * @throws SQLException if a database access error occurs or the pool is exhausted
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource.isClosed()) {
            throw new SQLException("Connection pool has been shut down - cannot obtain connections");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Tests whether a database connection can be established and executed.
     * 
     * <p>This method performs a health check by:
     * <ol>
     *   <li>Obtaining a connection from the pool</li>
     *   <li>Executing a simple query (SELECT 1)</li>
     *   <li>Validating the query returns expected result</li>
     *   <li>Returning the connection to the pool</li>
     * </ol>
     * 
     * @return true if health check passes, false otherwise
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            
            if (rs.next() && rs.getInt(1) == 1) {
                LOGGER.info("Database health check passed");
                return true;
            } else {
                LOGGER.warn("Database health check failed: unexpected query result");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.error("Database health check failed", e);
            return false;
        }
    }
    
    /**
     * Shuts down the HikariCP connection pool gracefully.
     * 
     * <p>This method should be called during application shutdown to ensure
     * all connections are properly closed and resources are released.</p>
     * 
     * <p><strong>Note:</strong> Call this method from {@code App.stop()} or
     * similar shutdown hooks.</p>
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOGGER.info("Shutting down HikariCP connection pool");
            try {
                dataSource.close();
            } catch (Exception e) {
                LOGGER.warn("Error during connection pool shutdown", e);
            }
        }
    }
    
    /**
     * Gets the underlying HikariDataSource instance.
     * 
     * <p>This method is primarily for monitoring and testing purposes.
     * Most code should use {@link #getConnection()} instead.</p>
     * 
     * <p><strong>Visibility:</strong> Package-private to prevent external
     * manipulation of the pool. Only accessible to tests and util package.</p>
     * 
     * @return the HikariDataSource instance
     */
    static HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Aplica las migraciones de base de datos pendientes usando Flyway.
     *
     * <p>Este método permite inicializar o actualizar el esquema de la base de datos
     * en tiempo de ejecución, sin necesidad de ejecutar {@code mvn flyway:migrate}.
     * Se invoca automáticamente desde {@code App.start()} antes de mostrar la pantalla
     * de inicio de sesión.</p>
     *
     * @throws IllegalStateException si las migraciones fallan o el pool no está disponible
     */
    public static void migrateDatabase() {
        LOGGER.info("Applying Flyway database migrations...");
        try {
            Flyway flyway = Flyway.configure(DatabaseConfig.class.getClassLoader())
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            var result = flyway.migrate();
            LOGGER.info("Flyway migration completed: {} migration(s) applied, schema version {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
        } catch (Exception e) {
            LOGGER.error("Flyway database migration failed", e);
            throw new IllegalStateException("Database migration failed: " + e.getMessage(), e);
        }
    }
}
