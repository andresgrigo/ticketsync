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
import java.util.List;

/**
 * Service class for admin user management operations.
 *
 * <p>Provides CRUD operations on the {@code users} table, delegating
 * persistence to {@link UserDAO}. All methods acquire their own
 * {@link Connection} via {@link DatabaseConfig#getConnection()} and
 * release it via try-with-resources.
 *
 * <p>All mutating operations log an audit trail entry at INFO level so
 * that administrator actions are recorded in the application log file
 * until the dedicated {@code audit_log} table is available.
 *
 * <p>Instances of this class are stateless and may be shared across
 * threads. The constructor performs no I/O.
 */
public class UserManagementService {

    private static final Logger LOGGER = LogManager.getLogger(UserManagementService.class);

    private final UserDAO userDAO = new UserDAOImpl();

    /**
     * Returns all users stored in the {@code users} table, ordered by
     * {@code user_id} ascending.
     *
     * @return list of all users; never {@code null}, may be empty
     * @throws SQLException if a database access error occurs
     */
    public List<User> getAllUsers() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return userDAO.findAll(conn);
        }
    }

    /**
     * Returns {@code true} if a user with the given username already exists
     * in the {@code users} table.
     *
     * <p>Intended for pre-flight validation in the Create User dialog so that
     * the uniqueness error can be displayed inline without waiting for a
     * database constraint violation.
     *
     * @param username the username to check; must not be {@code null}
     * @return {@code true} if the username is taken; {@code false} otherwise
     * @throws SQLException if a database access error occurs
     */
    public boolean usernameExists(String username) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return userDAO.findByUsername(conn, username).isPresent();
        }
    }

    /**
     * Creates a new user with the supplied credentials and role.
     *
     * <p>The raw password is hashed with BCrypt (cost factor 12) before
     * persisting. The method logs the creation at INFO level for audit
     * purposes.
     *
     * @param username      the desired username; must not be blank
     * @param rawPassword   the plaintext password to hash; must not be blank
     * @param role          the role to assign; must be {@code "ADMIN"} or {@code "VENDOR"}
     * @param adminUsername the username of the administrator performing the action
     * @return the generated {@code user_id} of the new record
     * @throws IllegalArgumentException if {@code role} is not a recognised value
     * @throws RuntimeException         if the username already exists (duplicate key)
     * @throws SQLException             if any other database access error occurs
     */
    public int createUser(String username, String rawPassword, String role, String adminUsername)
            throws SQLException {
        validateRole(role);
        String passwordHash = PasswordHasher.hashPassword(rawPassword);
        User user = new User(0, username, passwordHash, role, null);
        try (Connection conn = DatabaseConfig.getConnection()) {
            int newId = userDAO.insert(conn, user);
            LOGGER.info("Admin '{}' created user '{}' with role '{}'", adminUsername, username, role);
            return newId;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                throw new RuntimeException("Username '" + username + "' is already taken.");
            }
            throw e;
        }
    }

    /**
     * Updates the role of an existing user.
     *
     * <p>The username and password hash are preserved unchanged — only the
     * role field is modified. The update is logged at INFO level.
     *
     * @param existingUser  the current state of the user to update; must not be {@code null}
     * @param newRole       the replacement role; must be {@code "ADMIN"} or {@code "VENDOR"}
     * @param adminUsername the username of the administrator performing the action
     * @throws IllegalArgumentException if {@code newRole} is not a recognised value
     * @throws SQLException             if a database access error occurs
     */
    public void updateUserRole(User existingUser, String newRole, String adminUsername)
            throws SQLException {
        validateRole(newRole);
        User updated = new User(
                existingUser.getUserId(),
                existingUser.getUsername(),
                existingUser.getPasswordHash(),
                newRole,
                existingUser.getCreatedAt()
        );
        try (Connection conn = DatabaseConfig.getConnection()) {
            userDAO.update(conn, updated);
            LOGGER.info("Admin '{}' updated role of user '{}' to '{}'",
                    adminUsername, existingUser.getUsername(), newRole);
        }
    }

    /**
     * Deletes the user with the given primary key.
     *
     * <p>The deletion is logged at INFO level. The caller is responsible for
     * preventing deletion of the currently logged-in admin account.
     *
     * @param userId          the primary key of the user to delete
     * @param deletedUsername the username of the deleted user (for audit logging)
     * @param adminUsername   the username of the administrator performing the action
     * @throws SQLException if a database access error occurs
     */
    public void deleteUser(int userId, String deletedUsername, String adminUsername)
            throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            userDAO.delete(conn, userId);
            LOGGER.info("Admin '{}' deleted user '{}'", adminUsername, deletedUsername);
        }
    }

    /**
     * Validates that the supplied role is one of the accepted values.
     *
     * @param role the role string to validate
     * @throws IllegalArgumentException if the role is not {@code "ADMIN"} or {@code "VENDOR"}
     */
    private void validateRole(String role) {
        if (!"ADMIN".equals(role) && !"VENDOR".equals(role)) {
            throw new IllegalArgumentException("Invalid role: " + role + ". Must be ADMIN or VENDOR.");
        }
    }
}
