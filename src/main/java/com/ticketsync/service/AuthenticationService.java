package com.ticketsync.service;

import com.ticketsync.dao.UserDAO;
import com.ticketsync.dao.UserDAOImpl;
import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import com.ticketsync.util.PasswordHasher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Service responsible for user authentication and session lifecycle management.
 *
 * <p>Orchestrates the login workflow by coordinating {@link UserDAO} lookups,
 * BCrypt password verification via {@link PasswordHasher}, and session state
 * storage in {@link SessionContext}.
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>Failed login attempts are logged with a single generic warning regardless
 *       of the failure reason (missing user vs wrong password) to prevent
 *       username-enumeration attacks.</li>
 *   <li>Passwords are never logged.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AuthenticationService authService = new AuthenticationService();
 *
 * Optional<User> user = authService.login("admin", "admin123");
 * if (user.isPresent()) {
 *     // Authenticated — SessionContext is now populated
 * }
 *
 * authService.logout();  // Clears SessionContext
 * }</pre>
 *
 * @see SessionContext
 * @see UserDAO
 */
public class AuthenticationService {

    private static final Logger LOGGER = LogManager.getLogger(AuthenticationService.class);

    /** DAO used to look up users from the database. */
    private final UserDAO userDAO;

    /**
     * Production constructor — creates a live {@link UserDAOImpl} instance.
     */
    public AuthenticationService() {
        this.userDAO = new UserDAOImpl();
    }

    /**
     * Package-private constructor for test injection.
     *
     * <p>Allows unit tests to supply a mock or stub {@link UserDAO} without
     * requiring a live database connection.
     *
     * @param userDAO the DAO implementation to use; must not be {@code null}
     */
    AuthenticationService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Attempts to authenticate the given credentials against the database.
     *
     * <p>On success the authenticated {@link User} is stored in {@link SessionContext}
     * and returned wrapped in a non-empty {@link Optional}. On any failure (user not
     * found, wrong password) an empty {@link Optional} is returned and a single generic
     * warning is logged — the same message for both cases to prevent username enumeration.
     *
     * @param username the username to authenticate; must not be {@code null} or blank
     * @param password the plaintext password to verify; must not be {@code null}
     * @return a non-empty {@link Optional} containing the authenticated {@link User}
     *         on success, or {@link Optional#empty()} on any failure
     * @throws IllegalArgumentException if {@code username} is null/blank or
     *                                  {@code password} is null or blank
     * @throws SQLException if a database access error occurs
     */
    public Optional<User> login(String username, String password) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be null or blank");
        }

        LOGGER.info("Authentication attempt for username: {}", username);

        try (Connection conn = DatabaseConfig.getConnection()) {
            Optional<User> userOpt = userDAO.findByUsername(conn, username);
            if (userOpt.isPresent()
                    && PasswordHasher.verifyPassword(password, userOpt.get().getPasswordHash())) {
                User user = userOpt.get();
                SessionContext.getCurrentUser()
                        .ifPresent(prev -> LOGGER.info("Replacing existing session for '{}' on new login.", prev.getUsername()));
                SessionContext.clearCurrentUser();
                SessionContext.setCurrentUser(user);
                LOGGER.info("Authentication successful for username: {}", username);
                return Optional.of(user);
            }
        }

        // Generic warning — must NOT distinguish "user not found" from "wrong password"
        // to prevent log-based username enumeration (NFR-SEC04).
        LOGGER.warn("Authentication failed for username: {}", username);
        return Optional.empty();
    }

    /**
     * Logs out the currently authenticated user and clears the session context.
     *
     * <p>If no user is currently logged in this method is a no-op (no exception thrown).
     */
    public void logout() {
        SessionContext.getCurrentUser()
                .ifPresent(user -> LOGGER.info("User logged out: {}", user.getUsername()));
        SessionContext.clearCurrentUser();
    }
}
