package com.ticketsync.dao;

import com.ticketsync.model.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Objeto de Acceso a Datos para operaciones de base de datos de User.
 *
 * <h2>Participación en Transacciones</h2>
 * Todos los métodos aceptan un parámetro Connection para participar en transacciones de la capa de servicio.
 * Las implementaciones usan PreparedStatement para prevención de inyección SQL.
 *
 * <h2>Mapeo de Base de Datos</h2>
 * <ul>
 *   <li>Tabla de Base de Datos: users</li>
 *   <li>Clave Primaria: user_id (SERIAL)</li>
 *   <li>Restricción Única: username</li>
 * </ul>
 *
 * <h2>Seguridad</h2>
 * Las contraseñas se almacenan como hashes BCrypt, nunca en texto plano.
 * Las contraseñas se almacenan como hashes BCrypt usando jBCrypt.
 *
 * @see com.ticketsync.model.User
 */
public interface UserDAO {
    
    /**
     * Encuentra un usuario por clave primaria.
     *
     * @param conn Conexión de base de datos activa
     * @param userId Clave primaria del usuario a recuperar
     * @return Optional que contiene User si se encontró, vacío en caso contrario
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si userId es cero o negativo
     */
    Optional<User> findById(Connection conn, int userId) throws SQLException;
    
    /**
     * Encuentra un usuario por nombre de usuario para autenticación.
     * Utilizado en el flujo de inicio de sesión para validar credenciales.
     *
     * @param conn Conexión de base de datos activa
     * @param username Nombre de usuario a buscar
     * @return Optional que contiene User si se encontró, vacío en caso contrario
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si username es null o vacío
     */
    Optional<User> findByUsername(Connection conn, String username) throws SQLException;
    
    /**
     * Recupera todos los usuarios del sistema.
     * Utilizado en la interfaz de gestión de usuarios del administrador.
     *
     * @param conn Conexión de base de datos activa
     * @return Lista de todos los usuarios, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<User> findAll(Connection conn) throws SQLException;
    
    /**
     * Inserta un nuevo usuario en la base de datos.
     * La contraseña debe estar hasheada con BCrypt antes de llamar a este método.
     *
     * @param conn Conexión de base de datos activa
     * @param user Objeto User a insertar (el campo userId se ignora; la base de datos genera la clave).
     *        El campo {@code passwordHash} debe contener un hash BCrypt, nunca texto plano.
     * @return user_id generado por la base de datos
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si user es null
     */
    int insert(Connection conn, User user) throws SQLException;
    
    /**
     * Actualiza la información de un usuario existente.
     * Utilizado en la gestión de usuarios del administrador para modificar roles o restablecer contraseñas.
     *
     * @param conn Conexión de base de datos activa
     * @param user Objeto User con campos actualizados (userId debe estar establecido)
     * @throws SQLException si ocurre un error de acceso a la base de datos o el usuario no se encuentra
     */
    void update(Connection conn, User user) throws SQLException;
    
    /**
     * Elimina un usuario de la base de datos.
     * Utilizado en la interfaz de gestión de usuarios del administrador.
     *
     * <p><strong>Nota:</strong> La eliminación puede fallar debido a restricciones de clave foránea
     * si el usuario ha creado eventos o registros de ventas.
     * 
     * @param conn Conexión de base de datos activa
     * @param userId Clave primaria del usuario a eliminar
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     */
    void delete(Connection conn, int userId) throws SQLException;
}
