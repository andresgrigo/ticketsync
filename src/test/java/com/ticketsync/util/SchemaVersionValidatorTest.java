package com.ticketsync.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchemaVersionValidator.
 * 
 * <p>Note: These tests require PostgreSQL to be running with the ticketsync database
 * and migrations applied. Tests are enabled only when DB_TEST_ENABLED=true environment
 * variable is set to avoid failures in CI environments without database access.
 * 

 */
class SchemaVersionValidatorTest {
    
    /**
     * Tests schema version validation with a valid database setup.
     * 
     * <p>Prerequisites:
     * <ul>
     *   <li>PostgreSQL running on localhost:5432</li>
     *   <li>Database 'ticketsync' exists</li>
     *   <li>Flyway migrations applied (mvn flyway:migrate)</li>
     * </ul>
     * 
     * <p>To run this test:
     * <pre>
     * export DB_TEST_ENABLED=true  # Linux/macOS
     * set DB_TEST_ENABLED=true     # Windows CMD
     * $env:DB_TEST_ENABLED="true"  # Windows PowerShell
     * mvn test
     * </pre>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DB_TEST_ENABLED", matches = "true")
    void testSchemaVersionValidation() {
        // Given: Database with schema_version table containing valid version
        // When: validateSchemaVersion() is called
        // Then: No exceptions thrown, version matches application version
        assertDoesNotThrow(() -> SchemaVersionValidator.validateSchemaVersion());
    }
}
