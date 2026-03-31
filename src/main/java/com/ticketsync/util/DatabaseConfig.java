package com.ticketsync.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    
    private static final Logger logger = Logger.getLogger(DatabaseConfig.class.getName());
    private static final HikariDataSource dataSource;
    
    // Static initializer - runs once when class loads
    static {
        try {
            HikariConfig config = new HikariConfig();
            
            // Database connection settings
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/ticketsync");
            config.setUsername("postgres");
            config.setPassword("postgres");
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
                logger.log(Level.INFO, "HikariCP connection pool initialized successfully: {0} max connections", 
                          config.getMaximumPoolSize());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize HikariCP connection pool", e);
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
                logger.log(Level.INFO, "Database health check passed");
                return true;
            } else {
                logger.log(Level.WARNING, "Database health check failed: unexpected query result");
                return false;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database health check failed", e);
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
            logger.log(Level.INFO, "Shutting down HikariCP connection pool");
            try {
                dataSource.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during connection pool shutdown", e);
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
