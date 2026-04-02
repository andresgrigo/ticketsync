package com.ticketsync.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jasypt.properties.EncryptableProperties;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConfig provides a singleton connection pool using HikariCP.
 * 
 * <p>This class manages a pool of up to 5 database connections for concurrent
 * operations within a single application instance (booth or admin client).
 * The pool is initialized once when the class is first loaded and remains
 * active for the lifetime of the application.</p>
 * 
 * <p><strong>Pool Configuration:</strong></p>
 * <ul>
 *   <li>Maximum Pool Size: 5 connections (per application instance)</li>
 *   <li>Minimum Idle: 2 connections (maintained warm)</li>
 *   <li>Connection Timeout: 10 seconds</li>
 *   <li>Idle Timeout: 5 minutes</li>
 *   <li>Max Lifetime: 30 minutes</li>
 * </ul>
 * 
 * <p><strong>Usage Pattern:</strong></p>
 * <pre>{@code
 * Connection conn = null;
 * try {
 *     conn = DatabaseConfig.getConnection();
 *     // Use connection
 * } catch (SQLException e) {
 *     // Handle error
 * } finally {
 *     if (conn != null) {
 *         conn.close(); // Returns connection to pool
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>Note:</strong> Connection.close() returns the connection to the pool
 * rather than closing the underlying PostgreSQL connection.</p>
 * 

 * @version 1.0
 * @since 1.0
 */
public final class DatabaseConfig {
    
    private static final Logger LOGGER = LogManager.getLogger(DatabaseConfig.class);
    private static final HikariDataSource dataSource;
    
    // Static initializer - runs once when class loads
    static {
        try {
            // Step 1 — Read and validate master key
            String masterKey = System.getenv("TICKETSYNC_MASTER_KEY");
            if (masterKey == null || masterKey.isBlank()) {
                LOGGER.error("TICKETSYNC_MASTER_KEY environment variable is not set — cannot start application");
                throw new IllegalStateException("TICKETSYNC_MASTER_KEY environment variable is not set");
            }

            // Step 2 — Create encryptor and load EncryptableProperties
            EncryptableProperties props = new EncryptableProperties(EncryptionUtil.createEncryptor(masterKey));
            try (InputStream in = DatabaseConfig.class.getClassLoader()
                    .getResourceAsStream("jdbc.properties")) {
                if (in == null) {
                    throw new IllegalStateException("jdbc.properties not found on classpath");
                }
                props.load(in);
            }

            // Step 3 — Configure HikariCP from decrypted properties
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

            // Pool sizing for desktop app with concurrent operations:
            // - 1 persistent connection for PostgreSQL LISTEN/NOTIFY
            // - 1-2 connections for transaction processing
            // - 1-2 connections for concurrent queries/health checks
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(2);

            // Timeout configuration
            config.setConnectionTimeout(10000);  // 10 seconds max wait
            config.setIdleTimeout(300000);       // 5 minutes idle before release
            config.setMaxLifetime(1800000);      // 30 minutes max connection age

            // Health check configuration
            config.setConnectionTestQuery("SELECT 1");

            // Pool name for logging
            config.setPoolName("TicketSync-Pool");

            dataSource = new HikariDataSource(config);

            // Validate connectivity immediately to fail-fast if database is unreachable
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
     * Private constructor prevents instantiation of this utility class.
     * 
     * @throws UnsupportedOperationException always, to prevent instantiation
     */
    private DatabaseConfig() {
        throw new UnsupportedOperationException("DatabaseConfig is a utility class and cannot be instantiated");
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
}
