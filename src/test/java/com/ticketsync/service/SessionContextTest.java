package com.ticketsync.service;

import com.ticketsync.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SessionContext} and {@link AuthenticationService} input validation.
 *
 * <p>These tests require no database connection and run unconditionally (no
 * {@code DB_TEST_ENABLED} guard). They cover:
 * <ul>
 *   <li>VENDOR role positive case</li>
 *   <li>{@code hasRole()} returns {@code false} when no user is logged in</li>
 *   <li>Input validation guards on {@link AuthenticationService#login}</li>
 * </ul>
 */
class SessionContextTest {

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    // -----------------------------------------------------------------------
    // hasRole returns false when no user is logged in
    // -----------------------------------------------------------------------

    @Test
    void hasRole_whenNoUserLoggedIn_returnsFalse() {
        SessionContext.clearCurrentUser();
        assertFalse(SessionContext.hasRole("ADMIN"),
                "hasRole('ADMIN') must be false when no user is logged in");
    }

    @Test
    void hasRole_withNullRole_returnsFalse() {
        SessionContext.setCurrentUser(new User(1, "admin", "hash", "ADMIN", null));
        assertFalse(SessionContext.hasRole(null),
                "hasRole(null) must return false without throwing NPE");
    }

    // -----------------------------------------------------------------------
    // VENDOR role — positive case
    // -----------------------------------------------------------------------

    @Test
    void hasRole_vendorUser_returnsTrueForVendor() {
        User vendorUser = new User(2, "vendor1", "hash", "VENDOR", null);
        SessionContext.setCurrentUser(vendorUser);

        assertTrue(SessionContext.hasRole("VENDOR"), "hasRole('VENDOR') must be true for VENDOR user");
    }

    @Test
    void hasRole_vendorUser_returnsFalseForAdmin() {
        User vendorUser = new User(2, "vendor1", "hash", "VENDOR", null);
        SessionContext.setCurrentUser(vendorUser);

        assertFalse(SessionContext.hasRole("ADMIN"), "hasRole('ADMIN') must be false for VENDOR user");
    }

    // -----------------------------------------------------------------------
    // Input validation: AuthenticationService guards (no DB required — IAE
    // is thrown before any database connection is opened)
    // -----------------------------------------------------------------------

    @Test
    void login_withNullUsername_throwsIllegalArgumentException() {
        AuthenticationService service = new AuthenticationService();
        assertThrows(IllegalArgumentException.class,
                () -> service.login(null, "password"),
                "login() must throw IllegalArgumentException for null username");
    }

    @Test
    void login_withBlankUsername_throwsIllegalArgumentException() {
        AuthenticationService service = new AuthenticationService();
        assertThrows(IllegalArgumentException.class,
                () -> service.login("   ", "password"),
                "login() must throw IllegalArgumentException for blank username");
    }

    @Test
    void login_withNullPassword_throwsIllegalArgumentException() {
        AuthenticationService service = new AuthenticationService();
        assertThrows(IllegalArgumentException.class,
                () -> service.login("admin", null),
                "login() must throw IllegalArgumentException for null password");
    }

    @Test
    void login_withBlankPassword_throwsIllegalArgumentException() {
        AuthenticationService service = new AuthenticationService();
        assertThrows(IllegalArgumentException.class,
                () -> service.login("admin", "   "),
                "login() must throw IllegalArgumentException for blank password");
    }
}
