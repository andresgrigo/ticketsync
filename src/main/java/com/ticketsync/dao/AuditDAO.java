package com.ticketsync.dao;

import com.ticketsync.model.AuditLog;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Objeto de Acceso a Datos para operaciones de {@code audit_log}.
 */
public interface AuditDAO {

    /**
     * Inserta una fila de auditoría y devuelve el {@code log_id} generado.
     *
     * @param conn conexión de base de datos activa
     * @param auditLog registro de auditoría a insertar
     * @return {@code log_id} generado
     * @throws SQLException si la inserción falla
     */
    int insert(Connection conn, AuditLog auditLog) throws SQLException;

    /**
     * Devuelve filas de auditoría recientes en la ruta indexada timestamp/action.
     *
     * @param conn conexión de base de datos activa
     * @param fromInclusive límite de marca de tiempo inferior
     * @param toExclusive límite de marca de tiempo superior
     * @param limit máximo de filas a devolver
     * @return filas de auditoría coincidentes ordenadas por timestamp descendente luego action ascendente
     * @throws SQLException si la consulta falla
     */
    List<AuditLog> findRecent(Connection conn, LocalDateTime fromInclusive,
                              LocalDateTime toExclusive, int limit) throws SQLException;

    /**
     * Devuelve filas de auditoría recientes filtradas por una acción en la ruta indexada timestamp/action.
     *
     * @param conn conexión de base de datos activa
     * @param fromInclusive límite de marca de tiempo inferior
     * @param toExclusive límite de marca de tiempo superior
     * @param action filtro de acción
     * @param limit máximo de filas a devolver
     * @return filas de auditoría coincidentes ordenadas por timestamp descendente luego action ascendente
     * @throws SQLException si la consulta falla
     */
    List<AuditLog> findRecentByAction(Connection conn, LocalDateTime fromInclusive,
                                      LocalDateTime toExclusive, String action,
                                      int limit) throws SQLException;
}
