package com.ticketsync.dao;

import com.ticketsync.model.Event;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Objeto de Acceso a Datos para operaciones de base de datos de Event.
 *
 * <h2>Participación en Transacciones</h2>
 * Todos los métodos aceptan un parámetro Connection para participar en transacciones de la capa de servicio.
 * Las implementaciones usan PreparedStatement para prevención de inyección SQL.
 *
 * <h2>Mapeo de Base de Datos</h2>
 * <ul>
 *   <li>Tabla de Base de Datos: events</li>
 *   <li>Clave Primaria: event_id (SERIAL)</li>
 *   <li>Claves Foráneas: created_by → users.user_id</li>
 * </ul>
 *
 * @see com.ticketsync.model.Event
 */
public interface EventDAO {
    
    /**
     * Encuentra un evento por clave primaria.
     *
     * @param conn Conexión de base de datos activa
     * @param eventId Clave primaria del evento a recuperar
     * @return Optional que contiene Event si se encontró, vacío en caso contrario
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si eventId es cero o negativo
     */
    Optional<Event> findById(Connection conn, int eventId) throws SQLException;
    
    /**
     * Recupera todos los eventos del sistema.
     * Utilizado en la interfaz de gestión de eventos del administrador.
     *
     * @param conn Conexión de base de datos activa
     * @return Lista de todos los eventos, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<Event> findAll(Connection conn) throws SQLException;
    
    /**
     * Recupera solo los eventos activos para el selector de eventos del POS del vendedor.
     * Un evento está activo si {@code is_active = true}.
     * Utilizado en la interfaz POS del vendedor.
     *
     * @param conn Conexión de base de datos activa
     * @return Lista de eventos activos, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<Event> findActive(Connection conn) throws SQLException;
    
    /**
     * Inserta un nuevo evento en la base de datos.
     *
     * @param conn Conexión de base de datos activa
     * @param event Objeto Event a insertar (el campo eventId se ignora; la base de datos genera la clave)
     * @return event_id generado por la base de datos
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si event es null
     */
    int insert(Connection conn, Event event) throws SQLException;
    
    /**
     * Actualiza la información de un evento existente.
     * Utilizado en la gestión de eventos del administrador para modificar detalles o alternar estado activo.
     *
     * @param conn Conexión de base de datos activa
     * @param event Objeto Event con campos actualizados (eventId debe estar establecido)
     * @throws SQLException si ocurre un error de acceso a la base de datos o el evento no se encuentra
     */
    void update(Connection conn, Event event) throws SQLException;
    
    /**
     * Elimina un evento de la base de datos.
     * Utilizado en la interfaz de gestión de eventos del administrador.
     *
     * <p><strong>Nota:</strong> La eliminación puede fallar debido a restricciones de clave foránea
     * si el evento tiene zonas, asientos o registros de ventas asociados.
     *
     * @param conn Conexión de base de datos activa
     * @param eventId Clave primaria del evento a eliminar
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     */
    void delete(Connection conn, int eventId) throws SQLException;
}
