package com.ticketsync.service;

import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link AuthenticationService} and {@link SessionContext}.
 *
 * <p><strong>Prerequisites:</strong> PostgreSQL running on localhost:5432,
 * database {@code ticketsync} exists, Flyway migrations applied (including the
 * {@code admin} / {@code admin123} seed row from V002).
 *
 * <p><strong>Enable tests:</strong>
 * <pre>
 * # PowerShell
 * $env:DB_TEST_ENABLED="true"; mvn test
 * </pre>
 *
 * <p>Tests rely on the seeded {@code admin} account only and intentionally avoid
 * writing or deleting audit rows so the DB-gated suite remains safe to run against
 * a shared development database.
 */
class AuthenticationServiceIntegrationTest {

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");
        assumeTrue(adminSeedAvailable(),
                "Skipping DB test: database is unreachable or the admin seed row is missing");
        service = new AuthenticationService();
        SessionContext.clearCurrentUser();
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @Test
    void login_withValidAdminCredentials_returnsUser() throws SQLException {
        Optional<User> result = service.login("admin", "admin123");

        assertTrue(result.isPresent(), "login() must return a non-empty Optional for valid credentials");
        assertEquals("admin", result.get().getUsername(), "Returned user must have username 'admin'");
        assertEquals("ADMIN", result.get().getRole(), "Returned user must have role ADMIN");
        assertTrue(SessionContext.getCurrentUser().isPresent(),
                "SessionContext must be populated after successful login");
        assertEquals("admin", SessionContext.getCurrentUser().get().getUsername(),
                "SessionContext user must match the returned user");
    }

    @Test
    void login_withWrongPassword_returnsEmpty() throws SQLException {
        Optional<User> result = service.login("admin", "wrongpass");

        assertFalse(result.isPresent(), "login() must return empty Optional for wrong password");
        assertFalse(SessionContext.getCurrentUser().isPresent(),
                "SessionContext must remain empty after failed login");
    }

    @Test
    void login_withNonExistentUser_returnsEmpty() throws SQLException {
        Optional<User> result = service.login("nonexistent_xyz", "anypassword");

        assertFalse(result.isPresent(), "login() must return empty Optional for non-existent user");
    }

    @Test
    void logout_clearsSessionContext() throws SQLException {
        Optional<User> loginResult = service.login("admin", "admin123");
        assertTrue(loginResult.isPresent(), "Pre-condition: login must succeed");
        assertTrue(SessionContext.getCurrentUser().isPresent(), "Pre-condition: context must be populated");

        service.logout();

        assertFalse(SessionContext.getCurrentUser().isPresent(),
                "SessionContext must be empty after logout");
    }

    @Test
    void hasRole_adminUser_returnsTrueForAdmin() throws SQLException {
        service.login("admin", "admin123");

        assertTrue(SessionContext.hasRole("ADMIN"), "hasRole('ADMIN') must be true for admin user");
    }

    @Test
    void hasRole_adminUser_returnsFalseForVendor() throws SQLException {
        service.login("admin", "admin123");

        assertFalse(SessionContext.hasRole("VENDOR"), "hasRole('VENDOR') must be false for admin user");
    }

    private static boolean adminSeedAvailable() {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM users WHERE username = ? LIMIT 1")) {
            ps.setString(1, "admin");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }
}
