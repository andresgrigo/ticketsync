package com.ticketsync.dao;

import com.ticketsync.model.Zone;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Objeto de Acceso a Datos para operaciones de base de datos de Zone.
 *
 * <h2>Participación en Transacciones</h2>
 * Todos los métodos aceptan un parámetro Connection para participar en transacciones de la capa de servicio.
 * Las implementaciones usan PreparedStatement para prevención de inyección SQL.
 *
 * <h2>Mapeo de Base de Datos</h2>
 * <ul>
 *   <li>Tabla de Base de Datos: zones</li>
 *   <li>Clave Primaria: zone_id (SERIAL)</li>
 *   <li>Claves Foráneas: event_id → events.event_id</li>
 * </ul>
 *
 * @see com.ticketsync.model.Zone
 */
public interface ZoneDAO {
    
    /**
     * Encuentra una zona por clave primaria.
     *
     * @param conn Conexión de base de datos activa
     * @param zoneId Clave primaria de la zona a recuperar
     * @return Optional que contiene Zone si se encontró, vacío en caso contrario
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si zoneId es cero o negativo
     */
    Optional<Zone> findById(Connection conn, int zoneId) throws SQLException;
    
    /**
     * Recupera todas las zonas de un evento específico.
     * Utilizado en la configuración de zonas del administrador y el editor de plano de asientos.
     *
     * @param conn Conexión de base de datos activa
     * @param eventId ID del evento para recuperar zonas
     * @return Lista de zonas del evento, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<Zone> findByEventId(Connection conn, int eventId) throws SQLException;
    
    /**
     * Inserta una nueva zona en la base de datos.
     *
     * @param conn Conexión de base de datos activa
     * @param zone Objeto Zone a insertar (el campo zoneId se ignora; la base de datos genera la clave)
     * @return zone_id generado por la base de datos
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si zone es null
     */
    int insert(Connection conn, Zone zone) throws SQLException;
    
    /**
     * Actualiza la información de una zona existente.
     * Utilizado en la configuración de zonas del administrador para modificar nombres o precios.
     *
     * @param conn Conexión de base de datos activa
     * @param zone Objeto Zone con campos actualizados (zoneId debe estar establecido)
     * @throws SQLException si ocurre un error de acceso a la base de datos o la zona no se encuentra
     */
    void update(Connection conn, Zone zone) throws SQLException;
    
    /**
     * Elimina una zona de la base de datos.
     * Utilizado en la interfaz de configuración de zonas del administrador.
     *
     * <p><strong>Nota:</strong> La eliminación puede fallar debido a restricciones de clave foránea
     * si la zona tiene asientos asociados.
     *
     * @param conn Conexión de base de datos activa
     * @param zoneId Clave primaria de la zona a eliminar
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     */
    void delete(Connection conn, int zoneId) throws SQLException;
}
