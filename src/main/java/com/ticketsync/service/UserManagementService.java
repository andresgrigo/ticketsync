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
 * Clase de servicio para operaciones administrativas de gestión de usuarios.
 *
 * <p>Proporciona operaciones CRUD en la tabla {@code users}, delegando
 * la persistencia a {@link UserDAO}. Todos los métodos adquieren su propia
 * {@link Connection} vía {@link DatabaseConfig#getConnection()} y
 * la liberan vía try-with-resources.
 *
 * <p>Todas las operaciones mutantes registran una entrada de rastro de auditoría al nivel INFO
 * para que las acciones de administrador queden registradas en el archivo de log de la
 * aplicación hasta que la tabla {@code audit_log} dedicada esté disponible.
 *
 * <p>Las instancias de esta clase no tienen estado y pueden compartirse entre
 * hilos. El constructor no realiza I/O.
 */
public class UserManagementService {

    private static final Logger LOGGER = LogManager.getLogger(UserManagementService.class);

    private final UserDAO userDAO;
    private final AuditService auditService;
    private final ConnectionFactory connFactory;

    /**
     * Constructor de producción.
     */
    public UserManagementService() {
        this(new UserDAOImpl(), new AuditService(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección completa en pruebas unitarias.
     */
    UserManagementService(UserDAO userDAO, AuditService auditService, ConnectionFactory connFactory) {
        this.userDAO = userDAO;
        this.auditService = auditService;
        this.connFactory = connFactory;
    }

    /**
     * Devuelve todos los usuarios almacenados en la tabla {@code users}, ordenados por
     * {@code user_id} ascendente.
     *
     * @return lista de todos los usuarios; nunca {@code null}, puede estar vacía
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    public List<User> getAllUsers() throws SQLException {
        try (Connection conn = connFactory.get()) {
            return userDAO.findAll(conn);
        }
    }

    /**
     * Devuelve {@code true} si ya existe un usuario con el nombre de usuario dado
     * en la tabla {@code users}.
     *
     * <p>Destinado a la validación previa en el diálogo de Crear Usuario para que
     * el error de unicidad pueda mostrarse en línea sin esperar una
     * violación de restricción de base de datos.
     *
     * @param username el nombre de usuario a verificar; no debe ser {@code null}
     * @return {@code true} si el nombre de usuario está tomado; {@code false} en caso contrario
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    public boolean usernameExists(String username) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return userDAO.findByUsername(conn, username).isPresent();
        }
    }

    /**
     * Crea un nuevo usuario con las credenciales y rol suministrados.
     *
     * <p>La contraseña bruta se hashea con BCrypt (factor de coste 12) antes
     * de persistir. El método registra la creación al nivel INFO para propósitos de auditoría.
     *
     * @param username      el nombre de usuario deseado; no debe estar en blanco
     * @param rawPassword   la contraseña en texto plano a hashear; no debe estar en blanco
     * @param role          el rol a asignar; debe ser {@code "ADMIN"} o {@code "VENDOR"}
     * @param adminUsername el nombre de usuario del administrador que realiza la acción
     * @return el {@code user_id} generado del nuevo registro
     * @throws IllegalArgumentException si {@code role} no es un valor reconocido
     * @throws RuntimeException         si el nombre de usuario ya existe (clave duplicada)
     * @throws SQLException             si ocurre cualquier otro error de acceso a la base de datos
     */
    public int createUser(String username, String rawPassword, String role, String adminUsername)
            throws SQLException {
        validateRole(role);
        String passwordHash = PasswordHasher.hashPassword(rawPassword);
        User user = new User(0, username, passwordHash, role, null);
        try (Connection conn = connFactory.get()) {
            int newId = userDAO.insert(conn, user);
            LOGGER.info("Admin '{}' created user '{}' with role '{}'", adminUsername, username, role);
            auditService.logUserCreated(adminUsername, newId, username, role);
            return newId;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                throw new RuntimeException("Username '" + username + "' is already taken.");
            }
            throw e;
        }
    }

    /**
     * Actualiza el rol de un usuario existente.
     *
     * <p>El nombre de usuario y el hash de contraseña se preservan sin cambios — solo se
     * modifica el campo de rol. La actualización se registra al nivel INFO.
     *
     * @param existingUser  el estado actual del usuario a actualizar; no debe ser {@code null}
     * @param newRole       el rol de reemplazo; debe ser {@code "ADMIN"} o {@code "VENDOR"}
     * @param adminUsername el nombre de usuario del administrador que realiza la acción
     * @throws IllegalArgumentException si {@code newRole} no es un valor reconocido
     * @throws SQLException             si ocurre un error de acceso a la base de datos
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
        try (Connection conn = connFactory.get()) {
            userDAO.update(conn, updated);
            LOGGER.info("Admin '{}' updated role of user '{}' to '{}'",
                    adminUsername, existingUser.getUsername(), newRole);
            auditService.logUserRoleUpdated(adminUsername, existingUser, newRole);
        }
    }

    /**
    /**
     * Elimina el usuario con la clave primaria dada.
     *
     * <p>La eliminación se registra al nivel INFO. El llamador es responsable de
     * prevenir la eliminación de la cuenta de admin actualmente con sesión iniciada.
     *
     * @param userId          la clave primaria del usuario a eliminar
     * @param deletedUsername el nombre de usuario del usuario eliminado (para registro de auditoría)
     * @param adminUsername   el nombre de usuario del administrador que realiza la acción
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    public void deleteUser(int userId, String deletedUsername, String adminUsername)
            throws SQLException {
        try (Connection conn = connFactory.get()) {
            userDAO.delete(conn, userId);
            LOGGER.info("Admin '{}' deleted user '{}'", adminUsername, deletedUsername);
            auditService.logUserDeleted(adminUsername, userId, deletedUsername);
        }
    }

    /**
     * Valida que el rol suministrado sea uno de los valores aceptados.
     *
     * @param role la cadena de rol a validar
     * @throws IllegalArgumentException si el rol no es {@code "ADMIN"} ni {@code "VENDOR"}
     */
    private void validateRole(String role) {
        if (!"ADMIN".equals(role) && !"VENDOR".equals(role)) {
            throw new IllegalArgumentException("Invalid role: " + role + ". Must be ADMIN or VENDOR.");
        }
    }
}
