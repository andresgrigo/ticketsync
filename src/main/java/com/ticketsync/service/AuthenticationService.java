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
 * Servicio responsable de la autenticación de usuarios y gestión del ciclo de vida de la sesión.
 *
 * <p>Orquesta el flujo de trabajo de inicio de sesión coordinando consultas de {@link UserDAO},
 * verificación de contraseñas BCrypt vía {@link PasswordHasher} y almacenamiento del estado
 * de sesión en {@link SessionContext}.
 *
 * <h2>Seguridad</h2>
 * <ul>
 *   <li>Los intentos de inicio de sesión fallidos se registran con una advertencia genérica
 *       independientemente de la razón del fallo (usuario inexistente vs. contraseña incorrecta)
 *       para prevenir ataques de enumeración de nombres de usuario.</li>
 *   <li>Las contraseñas nunca se registran.</li>
 * </ul>
 *
 * <h2>Uso</h2>
 * <pre>{@code
 * AuthenticationService authService = new AuthenticationService();
 *
 * Optional<User> user = authService.login("admin", "admin123");
 * if (user.isPresent()) {
 *     // Autenticado — SessionContext ahora está poblado
 * }
 *
 * authService.logout();  // Limpia SessionContext
 * }</pre>
 *
 * @see SessionContext
 * @see UserDAO
 */
public class AuthenticationService {

    private static final Logger LOGGER = LogManager.getLogger(AuthenticationService.class);

    /** DAO usado para buscar usuarios en la base de datos. */
    private final UserDAO userDAO;
    private final AuditService auditService;
    private final ConnectionFactory connFactory;

    /**
     * Constructor de producción — crea una instancia activa de {@link UserDAOImpl}.
     */
    public AuthenticationService() {
        this(new UserDAOImpl(), new AuditService(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección en pruebas.
     *
     * <p>Permite a las pruebas unitarias suministrar un {@link UserDAO} simulado o stub
     * sin requerir una conexión activa a la base de datos.
     *
     * @param userDAO la implementación DAO a usar; no debe ser {@code null}
     */
    AuthenticationService(UserDAO userDAO) {
        this(userDAO, AuditService.noop(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección completa en pruebas unitarias.
     */
    AuthenticationService(UserDAO userDAO, AuditService auditService, ConnectionFactory connFactory) {
        this.userDAO = userDAO;
        this.auditService = auditService;
        this.connFactory = connFactory;
    }

    /**
     * Intenta autenticar las credenciales dadas contra la base de datos.
     *
     * <p>En caso de éxito, el {@link User} autenticado se almacena en {@link SessionContext}
     * y se devuelve envuelto en un {@link Optional} no vacío. En cualquier fallo (usuario no
     * encontrado, contraseña incorrecta) se devuelve un {@link Optional} vacío y se registra
     * una advertencia genérica — el mismo mensaje para ambos casos para prevenir enumeración
     * de nombres de usuario.
     *
     * @param username el nombre de usuario a autenticar; no debe ser {@code null} ni estar en blanco
     * @param password la contraseña en texto plano a verificar; no debe ser {@code null}
     * @return un {@link Optional} no vacío con el {@link User} autenticado en caso de éxito,
     *         o {@link Optional#empty()} en cualquier fallo
     * @throws IllegalArgumentException si {@code username} es null/blanco o {@code password} es null o blanco
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    public Optional<User> login(String username, String password) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be null or blank");
        }

        LOGGER.info("Authentication attempt for username: {}", username);

        try (Connection conn = connFactory.get()) {
            Optional<User> userOpt = userDAO.findByUsername(conn, username);
            if (userOpt.isPresent()
                    && PasswordHasher.verifyPassword(password, userOpt.get().getPasswordHash())) {
                User user = userOpt.get();
                SessionContext.getCurrentUser()
                        .ifPresent(prev -> LOGGER.info("Replacing existing session for '{}' on new login.", prev.getUsername()));
                SessionContext.clearCurrentUser();
                SessionContext.setCurrentUser(user);
                LOGGER.info("Authentication successful for username: {}", username);
                auditService.logLoginSuccess(username);
                return Optional.of(user);
            }
        }

            // Advertencia genérica — NO debe distinguir "usuario no encontrado" de "contraseña incorrecta"
            // para prevenir enumeración de nombres de usuario basada en registros.
        LOGGER.warn("Authentication failed for username: {}", username);
        auditService.logLoginFailure(username);
        return Optional.empty();
    }

    /**
     * Cierra la sesión del usuario autenticado actualmente y limpia el contexto de sesión.
     *
     * <p>Si ningún usuario tiene sesión iniciada este método es una operación sin efecto (no
     * lanza excepción).
     */
    public void logout() {
        SessionContext.getCurrentUser()
                .ifPresent(user -> LOGGER.info("User logged out: {}", user.getUsername()));
        SessionContext.clearCurrentUser();
    }
}
