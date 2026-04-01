package com.ticketsync.dao;

import com.ticketsync.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link UserDAO} for the {@code users} table.
 *
 * <p>All SQL is executed via {@link PreparedStatement} using {@code ?} placeholders.
 * No SQL is ever built by string concatenation, preventing SQL injection (OWASP A03).
 *
 * <p>Callers are responsible for managing the {@link Connection} lifecycle (open,
 * commit/rollback, close). This class never closes the supplied connection.
 *
 * <p>Password hash values are treated as opaque strings — this class never hashes
 * or logs password-related fields (NFR-SEC04).
 *
 * @see UserDAO
 * @see com.ticketsync.model.User
 */
public class UserDAOImpl implements UserDAO {

    // -------------------------------------------------------------------------
    // SQL constants
    // -------------------------------------------------------------------------

    private static final String SQL_FIND_BY_ID =
            "SELECT user_id, username, password_hash, role, created_at "
            + "FROM users WHERE user_id = ?";

    private static final String SQL_FIND_BY_USERNAME =
            "SELECT user_id, username, password_hash, role, created_at "
            + "FROM users WHERE username = ?";

    private static final String SQL_FIND_ALL =
            "SELECT user_id, username, password_hash, role, created_at "
            + "FROM users ORDER BY user_id ASC";

    private static final String SQL_INSERT =
            "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE users SET username = ?, password_hash = ?, role = ? WHERE user_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM users WHERE user_id = ?";

    // -------------------------------------------------------------------------
    // Public interface methods
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code userId} is zero or negative
     */
    @Override
    public Optional<User> findById(Connection conn, int userId) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive, got: " + userId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned {@link User} includes the {@code passwordHash} field so that
     * {@code AuthenticationService} can call {@code BCrypt.checkpw()}.
     *
     * @throws IllegalArgumentException if {@code username} is null or blank
     */
    @Override
    public Optional<User> findByUsername(Connection conn, String username) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_USERNAME)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<User> findAll(Connection conn) throws SQLException {
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code userId} field of {@code user} is ignored; the database-generated
     * {@code user_id} is returned. The {@code created_at} column is omitted from the
     * INSERT so {@code DEFAULT NOW()} applies.
     *
     * @throws IllegalArgumentException if {@code user} is null
     */
    @Override
    public int insert(Connection conn, User user) throws SQLException {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Insert succeeded but no generated key was returned");
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Updates {@code username}, {@code passwordHash}, and {@code role} for the
     * user identified by {@code user.getUserId()}.
     *
     * @throws IllegalArgumentException if {@code user} is null or {@code user.getUserId()} is zero or negative
     */
    @Override
    public void update(Connection conn, User user) throws SQLException {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (user.getUserId() <= 0) {
            throw new IllegalArgumentException("user.userId must be positive, got: " + user.getUserId());
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRole());
            ps.setInt(4, user.getUserId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("No user found for update with id: " + user.getUserId());
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code userId} is zero or negative
     */
    @Override
    public void delete(Connection conn, int userId) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive, got: " + userId);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps the current row of a {@link ResultSet} to a {@link User} object.
     *
     * <p>Handles a {@code null} {@code created_at} value gracefully by leaving
     * {@code User.createdAt} as {@code null}.
     *
     * @param rs ResultSet positioned on a valid row
     * @return populated {@link User}
     * @throws SQLException if a column value cannot be retrieved
     */
    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(rs.getString("role"));
        Timestamp ts = rs.getTimestamp("created_at");
        user.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        return user;
    }
}
