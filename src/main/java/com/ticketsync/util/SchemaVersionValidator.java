package com.ticketsync.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Validates database schema version matches application version on startup.
 * 
 * <p>This utility implements requirement: "Application prevents startup 
 * when schema version is incompatible with application version."
 * 
 * <p>Usage:
 * <pre>
 * SchemaVersionValidator.validateSchemaVersion();
 * </pre>
 * 

 * @version 1.0-SNAPSHOT
 */
public class SchemaVersionValidator {
    
    // Database connection parameters (will be encrypted)
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/ticketsync";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";
    
    // Application version from pom.xml
    private static final String EXPECTED_VERSION = "1.0-SNAPSHOT";
    
    /**
     * Validates that the database schema version matches the application version.
     * 
     * <p>Queries the schema_version table and compares the version with the expected
     * application version. Throws RuntimeException if:
     * <ul>
     *   <li>Database connection fails</li>
     *   <li>schema_version table is missing</li>
     *   <li>Version mismatch detected</li>
     * </ul>
     * 
     * @throws RuntimeException if validation fails or database connection errors occur
     */
    public static void validateSchemaVersion() {
        String query = "SELECT version FROM schema_version ORDER BY applied_at DESC LIMIT 1";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                String dbVersion = rs.getString("version");
                
                if (dbVersion == null) {
                    throw new RuntimeException("schema_version table contains NULL version! " +
                                               "Database schema is corrupted.");
                }
                
                // Trim whitespace to handle formatting differences
                dbVersion = dbVersion.trim();

                if (!EXPECTED_VERSION.equals(dbVersion)) {
                    throw new RuntimeException(
                        String.format("Schema version mismatch! Expected: %s, Found: %s. " +
                                      "Run 'mvn flyway:migrate' to update schema.",
                                      EXPECTED_VERSION, dbVersion)
                    );
                }
                
                System.out.println("✓ Schema version validated: " + dbVersion);
            } else {
                throw new RuntimeException("schema_version table is empty! " +
                                           "Run 'mvn flyway:migrate' to initialize schema.");
            }
            
        } catch (SQLException e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null) {
                errorMsg = "Unknown SQL error (" + e.getClass().getSimpleName() + ")";
            }
            
            // Provide specific guidance based on error type
            if (errorMsg.contains("Connection") || errorMsg.contains("connection")) {
                throw new RuntimeException(
                    "Failed to connect to database. Ensure PostgreSQL is running on localhost:5432.\n" +
                    "Start database: docker-compose up -d\nError: " + errorMsg, e);
            } else if (errorMsg.contains("does not exist")) {
                throw new RuntimeException(
                    "schema_version table not found. Run migrations first: mvn flyway:migrate\n" +
                    "Error: " + errorMsg, e);
            } else if (errorMsg.contains("authentication") || errorMsg.contains("password")) {
                throw new RuntimeException(
                    "Database authentication failed. Check credentials in SchemaVersionValidator.\n" +
                    "Error: " + errorMsg, e);
            } else {
                throw new RuntimeException("Failed to validate schema version: " + errorMsg, e);
            }
        }
    }
}
