package com.ticketsync.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa una fila en la tabla {@code audit_log}.
 */
public class AuditLog {

    private int logId;
    private LocalDateTime timestamp;
    private String username;
    private String action;
    private String entityType;
    private Integer entityId;
    private String details;
    private String ipAddress;
    private String sessionId;

    /**
     * Constructor por defecto para mapeo JDBC.
     */
    public AuditLog() {
    }

    /**
     * Construye un registro de auditoría con todos los campos mapeados.
     *
     * @param logId       clave primaria generada por la base de datos
     * @param timestamp   fecha y hora en que ocurrió el evento; no debe ser null
     * @param username    nombre de usuario del actor que desencadenó el evento; no debe estar en blanco
     * @param action      nombre de la constante de acción (ej. {@code "PURCHASE_SEATS"}); no debe estar en blanco
     * @param entityType  tipo de entidad afectada (ej. {@code "SALE"}, {@code "USER"}); puede ser null
     * @param entityId    clave primaria de la entidad afectada; puede ser null
     * @param details     cadena JSON con contexto adicional; puede ser null
     * @param ipAddress   dirección IP del cliente; puede ser null
     * @param sessionId   identificador de sesión; puede ser null
     */
    public AuditLog(int logId, LocalDateTime timestamp, String username, String action,
                    String entityType, Integer entityId, String details,
                    String ipAddress, String sessionId) {
        this.logId = logId;
        this.timestamp = timestamp;
        this.username = username;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.sessionId = sessionId;
    }

    /**
     * Devuelve el identificador del registro.
     *
     * @return clave primaria generada por la base de datos
     */
    public int getLogId() {
        return logId;
    }

    /**
     * Establece el identificador del registro.
     *
     * @param logId clave primaria generada por la base de datos
     */
    public void setLogId(int logId) {
        this.logId = logId;
    }

    /**
     * Devuelve la marca de tiempo del evento.
     *
     * @return fecha y hora en que ocurrió el evento auditado
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Establece la marca de tiempo del evento.
     *
     * @param timestamp fecha y hora del evento; no debe ser null
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Devuelve el nombre de usuario del actor.
     *
     * @return nombre de usuario que desencadenó el evento
     */
    public String getUsername() {
        return username;
    }

    /**
     * Establece el nombre de usuario del actor.
     *
     * @param username nombre de usuario del actor; no debe ser null ni estar en blanco
     */
    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        this.username = username;
    }

    /**
     * Devuelve el nombre de la acción.
     *
     * @return nombre de la constante de acción (ej. {@code "PURCHASE_SEATS"})
     */
    public String getAction() {
        return action;
    }

    /**
     * Establece el nombre de la acción.
     *
     * @param action nombre de la constante de acción; no debe ser null ni estar en blanco
     */
    public void setAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be null or blank");
        }
        this.action = action;
    }

    /**
     * Devuelve el tipo de entidad afectada.
     *
     * @return cadena de tipo de entidad (ej. {@code "SALE"}, {@code "USER"}), o null
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Establece el tipo de entidad afectada.
     *
     * @param entityType cadena de tipo de entidad; puede ser null
     */
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    /**
     * Devuelve la clave primaria de la entidad afectada.
     *
     * @return clave primaria de la entidad, o null si no aplica
     */
    public Integer getEntityId() {
        return entityId;
    }

    /**
     * Establece la clave primaria de la entidad afectada.
     *
     * @param entityId clave primaria de la entidad; puede ser null
     */
    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    /**
     * Devuelve la cadena JSON de detalles.
     *
     * @return cadena JSON con contexto adicional del evento, o null
     */
    public String getDetails() {
        return details;
    }

    /**
     * Establece la cadena JSON de detalles.
     *
     * @param details cadena JSON con contexto adicional; puede ser null
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * Devuelve la dirección IP del cliente.
     *
     * @return cadena de dirección IP, o null si no fue registrada
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Establece la dirección IP del cliente.
     *
     * @param ipAddress dirección IP del cliente; puede ser null
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Devuelve el identificador de sesión.
     *
     * @return ID de sesión asociado al evento, o null si no fue registrado
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Establece el identificador de sesión.
     *
     * @param sessionId ID de sesión; puede ser null
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AuditLog auditLog = (AuditLog) o;
        return logId == auditLog.logId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(logId);
    }

    @Override
    public String toString() {
        return "AuditLog{"
                + "logId=" + logId
                + ", timestamp=" + timestamp
                + ", username='" + username + '\''
                + ", action='" + action + '\''
                + ", entityType='" + entityType + '\''
                + ", entityId=" + entityId
                + '}';
    }
}
