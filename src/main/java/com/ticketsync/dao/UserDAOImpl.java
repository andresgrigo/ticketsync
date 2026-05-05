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
 * Implementación JDBC de {@link UserDAO} para la tabla {@code users}.
 *
 * <p>Todo el SQL se ejecuta vía {@link PreparedStatement} usando marcadores {@code ?}.
 * Nunca se construye SQL por concatenación de cadenas, previniendo inyección SQL (OWASP A03).
 *
 * <p>Los llamadores son responsables de gestionar el ciclo de vida de {@link Connection} (abrir,
 * commit/rollback, cerrar). Esta clase nunca cierra la conexión suministrada.
 *
 * <p>Los valores de hash de contraseña se tratan como cadenas opacas — esta clase nunca hashea
 * ni registra campos relacionados con contraseñas.
 *
 * @see UserDAO
 * @see com.ticketsync.model.User
 */
public class UserDAOImpl implements UserDAO {

    /** Crea un nuevo UserDAOImpl usando la fábrica de conexiones de producción. */
    public UserDAOImpl() { }

    // Constantes SQL
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
    // Métodos públicos de interfaz
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException si {@code userId} es cero o negativo
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
     * <p>El {@link User} devuelto incluye el campo {@code passwordHash} para que
     * {@code AuthenticationService} pueda llamar a {@code BCrypt.checkpw()}.
     *
     * @throws IllegalArgumentException si {@code username} es null o está en blanco
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
     * <p>El campo {@code userId} de {@code user} se ignora; el {@code user_id} generado por la
     * base de datos es devuelto. La columna {@code created_at} se omite del
     * INSERT para que aplique {@code DEFAULT NOW()}.
     *
     * @throws IllegalArgumentException si {@code user} es null
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
     * <p>Actualiza {@code username}, {@code passwordHash} y {@code role} para el
     * usuario identificado por {@code user.getUserId()}.
     *
     * @throws IllegalArgumentException si {@code user} es null o {@code user.getUserId()} es cero o negativo
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
     * @throws IllegalArgumentException si {@code userId} es cero o negativo
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
    // Ayudantes privados
    // -------------------------------------------------------------------------

    /**
     * Mapea la fila actual de un {@link ResultSet} a un objeto {@link User}.
     *
     * <p>Maneja un valor {@code null} en {@code created_at} de forma elegante dejando
     * {@code User.createdAt} como {@code null}.
     *
     * @param rs ResultSet posicionado en una fila válida
     * @return {@link User} poblado
     * @throws SQLException si un valor de columna no puede ser recuperado
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
