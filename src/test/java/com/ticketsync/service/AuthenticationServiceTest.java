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
 * <p>Tests rely on the seeded {@code admin} account only — no rows are inserted
 * or deleted, so no teardown cleanup is required beyond clearing thread-local state.
 */
class AuthenticationServiceTest {

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");
        service = new AuthenticationService();
        SessionContext.clearCurrentUser();
    }

    @AfterEach
    void tearDown() {
        // Always clear thread-local state to prevent leakage between tests.
        SessionContext.clearCurrentUser();
    }

    // -----------------------------------------------------------------------
    // Successful login with seeded admin credentials
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Wrong password returns empty Optional; SessionContext stays empty
    // -----------------------------------------------------------------------

    @Test
    void login_withWrongPassword_returnsEmpty() throws SQLException {
        Optional<User> result = service.login("admin", "wrongpass");

        assertFalse(result.isPresent(), "login() must return empty Optional for wrong password");
        assertFalse(SessionContext.getCurrentUser().isPresent(),
                "SessionContext must remain empty after failed login");
    }

    // -----------------------------------------------------------------------
    // Non-existent username returns empty Optional
    // -----------------------------------------------------------------------

    @Test
    void login_withNonExistentUser_returnsEmpty() throws SQLException {
        Optional<User> result = service.login("nonexistent_xyz", "anypassword");

        assertFalse(result.isPresent(), "login() must return empty Optional for non-existent user");
    }

    @Test
    void login_withValidAdminCredentials_writesLoginSuccessAudit() throws SQLException {
        int previousMaxId = maxAuditLogId();

        Optional<User> result = service.login("admin", "admin123");

        assertTrue(result.isPresent(), "login() must succeed before audit verification");
        AuditRow row = findAuditLogAfter(previousMaxId, "LOGIN_SUCCESS", "admin");
        assertTrue(row != null, "login success should write an audit row");
        assertTrue(row.details().contains("\"outcome\":\"success\""));
        deleteAuditLog(row.id());
    }

    @Test
    void login_withWrongPassword_writesLoginFailureAudit() throws SQLException {
        int previousMaxId = maxAuditLogId();

        Optional<User> result = service.login("admin", "wrongpass");

        assertFalse(result.isPresent(), "login must fail before failure-audit verification");
        AuditRow row = findAuditLogAfter(previousMaxId, "LOGIN_FAILURE", "admin");
        assertTrue(row != null, "login failure should write an audit row");
        assertFalse(row.details().toLowerCase().contains("password"));
        deleteAuditLog(row.id());
    }

    // -----------------------------------------------------------------------
    // Logout clears SessionContext
    // -----------------------------------------------------------------------

    @Test
    void logout_clearsSessionContext() throws SQLException {
        // Arrange: log in first
        Optional<User> loginResult = service.login("admin", "admin123");
        assertTrue(loginResult.isPresent(), "Pre-condition: login must succeed");
        assertTrue(SessionContext.getCurrentUser().isPresent(), "Pre-condition: context must be populated");

        // Act
        service.logout();

        // Assert
        assertFalse(SessionContext.getCurrentUser().isPresent(),
                "SessionContext must be empty after logout");
    }

    // -----------------------------------------------------------------------
    // hasRole returns true for admin user's role, false for other roles
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // VENDOR positive case and no-DB tests — see SessionContextTest
    // -----------------------------------------------------------------------

    private int maxAuditLogId() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(MAX(log_id), 0) FROM audit_log");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private AuditRow findAuditLogAfter(int previousMaxId, String action, String username)
            throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT log_id, details FROM audit_log "
                             + "WHERE log_id > ? AND action = ? AND username = ? "
                             + "ORDER BY log_id DESC LIMIT 1")) {
            ps.setInt(1, previousMaxId);
            ps.setString(2, action);
            ps.setString(3, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new AuditRow(rs.getInt(1), rs.getString(2));
            }
        }
    }

    private void deleteAuditLog(int logId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM audit_log WHERE log_id = ?")) {
            ps.setInt(1, logId);
            ps.executeUpdate();
        }
    }

    private record AuditRow(int id, String details) {
    }
}
