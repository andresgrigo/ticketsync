package com.ticketsync.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents one row in the {@code audit_log} table.
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
     * Default constructor for JDBC mapping.
     */
    public AuditLog() {
    }

    /**
     * Constructs an audit log with all mapped fields.
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

    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be null or blank");
        }
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Integer getEntityId() {
        return entityId;
    }

    public void setEntityId(Integer entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getSessionId() {
        return sessionId;
    }

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
