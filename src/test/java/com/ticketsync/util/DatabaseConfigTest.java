package com.ticketsync.util;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseConfig HikariCP connection pool.
 * 
 * <p>These tests validate that the HikariCP connection pool is properly configured
 * with the correct pool sizing, timeout settings, and health check functionality.
 * 
 * <p>SchemaVersionValidator uses HikariCP connection pool via {@link DatabaseConfig}
 * for database access.
 * 
 * <p><strong>Prerequisites:</strong>
 * <ul>
 *   <li>PostgreSQL running on localhost:5432</li>
 *   <li>Database 'ticketsync' exists</li>
 *   <li>Credentials: postgres/postgres (development)</li>
 * </ul>
 * 
 * <p><strong>Running Tests:</strong>
 * <pre>
 * # Enable database tests
 * export DB_TEST_ENABLED=true  # Linux/macOS
 * set DB_TEST_ENABLED=true     # Windows CMD
 * $env:DB_TEST_ENABLED="true"  # Windows PowerShell
 * 
 * # Run tests
 * mvn test
 * </pre>
 * 

 * @version 1.0
 */
class DatabaseConfigTest {
    
    /**
     * Tests that the HikariCP connection pool initializes successfully.       
     * 
     * <p>Validates:
     * <ul>
     *   <li>DataSource is not null</li>
     *   <li>DataSource is not closed</li>
     *   <li>Pool is ready to accept connections</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true")
    void testConnectionPoolInitializes() {
        // Given: DatabaseConfig class loaded
        HikariDataSource dataSource = DatabaseConfig.getDataSource();
        
        // Then: DataSource is initialized and active
        assertNotNull(dataSource, "HikariDataSource should be initialized");   
        assertFalse(dataSource.isClosed(), "DataSource should not be closed"); 
        assertTrue(dataSource.isRunning(), "DataSource should be running");    
    }
    
    /**
     * Tests that getConnection() returns a valid, usable connection from the pool.
     * 
     * <p>Validates:
     * <ul>
     *   <li>Connection is not null</li>
     *   <li>Connection is not closed</li>
     *   <li>Connection is valid</li>
     *   <li>Connection can be returned to pool via close()</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true") 
    void testGetConnectionReturnsValidConnection() throws SQLException {       
        // Given: Connection pool is initialized
        Connection conn = null;
        
        try {
            // When: getConnection() is called
            conn = DatabaseConfig.getConnection();

            // Then: Connection is valid and usable
            assertNotNull(conn, "Connection should not be null");
            assertFalse(conn.isClosed(), "Connection should not be closed");   
            assertTrue(conn.isValid(2), "Connection should be valid within 2 seconds");

        } finally {
            // Clean up: Return connection to pool
            if (conn != null) {
                conn.close();
            }
        }
    }
    
    /**
     * Tests that health check validation executes successfully.
     * 
     * <p>Validates:
     * <ul>
     *   <li>testConnection() returns true</li>
     *   <li>SELECT 1 query executes without errors</li>
     *   <li>Connection is returned to pool after health check</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true") 
    void testHealthCheckExecutesSuccessfully() {
        // When: testConnection() health check is called
        boolean healthCheckPassed = DatabaseConfig.testConnection();
        
        // Then: Health check passes
        assertTrue(healthCheckPassed, "Database health check should pass");
    }
    
    /**
     * Tests that health check query returns expected result.
     * 
     * <p>Manually executes the same health check query that HikariCP uses     
     * to validate connection testing is working correctly.
     * 
     * <p>Validates:
     * <ul>
     *   <li>SELECT 1 query executes</li>
     *   <li>Result set contains expected value (1)</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true") 
    void testHealthCheckQueryReturnsExpectedResult() throws SQLException {     
        // Using try-with-resources to prevent resource leaks
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            // Then: Result is correct
            assertTrue(rs.next(), "Result set should have at least one row");  
            assertEquals(1, rs.getInt(1), "SELECT 1 should return value 1");   
            assertFalse(rs.next(), "Result set should have exactly one row");  
        }
    }
    
    /**
     * Tests that connection checkout completes within 50ms performance requirement.
     * 
     * <p>This test validates: Connection checkout must complete in under      
     * 50ms under normal load to prevent performance bottlenecks.
     * 
     * <p>Validates:
     * <ul>
     *   <li>getConnection() completes in &lt;50ms</li>
     *   <li>Connection pool maintains "warm" connections for fast checkout</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> Uses multiple warm-up runs and averages to reduce
     * flakiness from GC pauses or CI system load.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true") 
    void testConnectionCheckoutPerformance() throws SQLException {
        // Warm up the pool
        for (int i = 0; i < 3; i++) {
            try (Connection warmup = DatabaseConfig.getConnection()) {
                // Warm-up checkout
            }
        }
        
        // Measure 5 runs and use average to reduce variance
        long totalDuration = 0;
        for (int i = 0; i < 5; i++) {
            long startTime = System.currentTimeMillis();
            try (Connection conn = DatabaseConfig.getConnection()) {
                long duration = System.currentTimeMillis() - startTime;
                totalDuration += duration;
            }
        }
        
        long averageDuration = totalDuration / 5;
        
        // Average checkout should be under 50ms
        assertTrue(averageDuration < 50, 
                  String.format("Average connection checkout took %dms (over 5 runs), exceeds 50ms limit", 
                               averageDuration));
    }
    
    /**
     * Tests that HikariCP pool is configured with correct pool sizing.        
     * 
     * <p>Validates pool sizing for desktop app with concurrent operations:    
     * <ul>
     *   <li>Maximum pool size = 5 (main transactions + background listeners)</li>
     *   <li>Minimum idle = 2 (warm connections)</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true") 
    void testConnectionPoolSizingConfiguration() {
        // Given: DatabaseConfig initialized
        HikariDataSource dataSource = DatabaseConfig.getDataSource();
        
        // Then: Pool sizing matches configuration

        assertEquals(5, dataSource.getMaximumPoolSize(), 
                    "Maximum pool size should be 5 for desktop app with background threads");
        assertEquals(2, dataSource.getMinimumIdle(), 
                    "Minimum idle should be 2 (warm connections)");
    }
    
    /**
     * Tests that HikariCP pool is configured with correct timeout settings.   
     * 
     * <p>Validates timeout configuration:
     * <ul>
     *   <li>Connection timeout = 10000ms (10 seconds)</li>
     *   <li>Idle timeout = 300000ms (5 minutes)</li>
     *   <li>Max lifetime = 1800000ms (30 minutes)</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true") 
    void testConnectionPoolTimeoutConfiguration() {
        // Given: DatabaseConfig initialized
        HikariDataSource dataSource = DatabaseConfig.getDataSource();
        
        // Then: Timeout settings match architecture requirements
        assertEquals(10000, dataSource.getConnectionTimeout(), 
                    "Connection timeout should be 10 seconds (10000ms)");      
        assertEquals(300000, dataSource.getIdleTimeout(), 
                    "Idle timeout should be 5 minutes (300000ms)");
        assertEquals(1800000, dataSource.getMaxLifetime(), 
                    "Max lifetime should be 30 minutes (1800000ms)");
    }
    
    /**
     * Tests that multiple connections can be obtained from the pool concurrently.
     * 
     * <p>This test validates that the pool can handle multiple simultaneous   
     * connection checkouts without errors.
     * 
     * <p>Validates:
     * <ul>
     *   <li>Pool supports multiple concurrent connections</li>
     *   <li>All connections are valid and independent</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true")
    void testMultipleConnectionsFromPool() throws SQLException {
        Connection conn1 = null;
        Connection conn2 = null;
        Connection conn3 = null;
        
        try {
            // When: Multiple connections are obtained simultaneously
            conn1 = DatabaseConfig.getConnection();
            conn2 = DatabaseConfig.getConnection();
            conn3 = DatabaseConfig.getConnection();

            // Then: All connections are valid and independent
            assertNotNull(conn1, "First connection should not be null");       
            assertNotNull(conn2, "Second connection should not be null");      
            assertNotNull(conn3, "Third connection should not be null");       

            assertTrue(conn1.isValid(2), "First connection should be valid");  
            assertTrue(conn2.isValid(2), "Second connection should be valid"); 
            assertTrue(conn3.isValid(2), "Third connection should be valid");  

            // Verify they are different connection instances
            assertNotSame(conn1, conn2, "Connections should be different instances");
            assertNotSame(conn2, conn3, "Connections should be different instances");
            assertNotSame(conn1, conn3, "Connections should be different instances");

        } finally {
            // Clean up: Return all connections to pool
            if (conn1 != null) conn1.close();
            if (conn2 != null) conn2.close();
            if (conn3 != null) conn3.close();
        }
    }
    
    /**
     * Tests that connections are properly returned to the pool after close(). 
     * 
     * <p>Validates:
     * <ul>
     *   <li>Connection.close() returns connection to pool</li>
     *   <li>Closed connections can be reused by pool</li>
     *   <li>Pool maintains connection count correctly</li>
     * </ul>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true") 
    void testConnectionReturnedToPoolAfterClose() throws SQLException {        
        // Given: Initial active connections count
        HikariDataSource dataSource = DatabaseConfig.getDataSource();
        int initialActive = dataSource.getHikariPoolMXBean().getActiveConnections();
        
        Connection conn = null;
        try {
            // When: Connection is obtained
            conn = DatabaseConfig.getConnection();
            int activeWithConnection = dataSource.getHikariPoolMXBean().getActiveConnections();

            // Then: Active connection count increases
            assertEquals(initialActive + 1, activeWithConnection, 
                        "Active connections should increase by 1");

        } finally {
            // When: Connection is closed
            if (conn != null) {
                conn.close();
            }
        }
        
        // Then: Active connection count returns to initial value
        int finalActive = dataSource.getHikariPoolMXBean().getActiveConnections();
        assertEquals(initialActive, finalActive, 
                    "Active connections should return to initial count after close()");
    }
}
