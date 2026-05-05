package com.ticketsync.dao;

import com.ticketsync.model.AuditLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC de {@link AuditDAO}.
 */
public class AuditDAOImpl implements AuditDAO {

    private static final Logger LOGGER = LogManager.getLogger(AuditDAOImpl.class);

    /** Crea un nuevo AuditDAOImpl usando la fábrica de conexiones de producción. */
    public AuditDAOImpl() { }

    private static final String SQL_INSERT =
            "INSERT INTO audit_log (timestamp, username, action, entity_type, entity_id, details, ip_address, session_id) "
                    + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS inet), ?)";

    private static final String SQL_FIND_RECENT =
            "SELECT log_id, timestamp, username, action, entity_type, entity_id, details, ip_address, session_id "
                    + "FROM audit_log "
                    + "WHERE timestamp >= ? AND timestamp < ? "
                    + "ORDER BY timestamp DESC, action ASC "
                    + "LIMIT ?";

    private static final String SQL_FIND_RECENT_BY_ACTION =
            "SELECT log_id, timestamp, username, action, entity_type, entity_id, details, ip_address, session_id "
                    + "FROM audit_log "
                    + "WHERE timestamp >= ? AND timestamp < ? AND action = ? "
                    + "ORDER BY timestamp DESC, action ASC "
                    + "LIMIT ?";

    @Override
    public int insert(Connection conn, AuditLog auditLog) throws SQLException {
        if (auditLog == null) {
            throw new IllegalArgumentException("auditLog must not be null");
        }
        if (auditLog.getTimestamp() == null) {
            throw new IllegalArgumentException("auditLog.timestamp must not be null");
        }
        LOGGER.debug("Inserting audit log action={} entityType={} entityId={}",
                auditLog.getAction(), auditLog.getEntityType(), auditLog.getEntityId());
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, Timestamp.valueOf(auditLog.getTimestamp()));
            ps.setString(2, auditLog.getUsername());
            ps.setString(3, auditLog.getAction());
            ps.setString(4, auditLog.getEntityType());
            if (auditLog.getEntityId() == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, auditLog.getEntityId());
            }
            ps.setString(6, auditLog.getDetails());
            ps.setString(7, auditLog.getIpAddress());
            ps.setString(8, auditLog.getSessionId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Insert succeeded but no generated audit key was returned");
            }
        }
    }

    @Override
    public List<AuditLog> findRecent(Connection conn, LocalDateTime fromInclusive,
                                     LocalDateTime toExclusive, int limit) throws SQLException {
        validateTimeRange(fromInclusive, toExclusive, limit);
        LOGGER.debug("Querying recent audit logs from {} to {} limit={}",
                fromInclusive, toExclusive, limit);
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_RECENT)) {
            ps.setTimestamp(1, Timestamp.valueOf(fromInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(toExclusive));
            ps.setInt(3, limit);
            return readRows(ps);
        }
    }

    @Override
    public List<AuditLog> findRecentByAction(Connection conn, LocalDateTime fromInclusive,
                                             LocalDateTime toExclusive, String action,
                                             int limit) throws SQLException {
        validateTimeRange(fromInclusive, toExclusive, limit);
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be null or blank");
        }
        LOGGER.debug("Querying recent audit logs from {} to {} action={} limit={}",
                fromInclusive, toExclusive, action, limit);
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_RECENT_BY_ACTION)) {
            ps.setTimestamp(1, Timestamp.valueOf(fromInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(toExclusive));
            ps.setString(3, action);
            ps.setInt(4, limit);
            return readRows(ps);
        }
    }

    private static void validateTimeRange(LocalDateTime fromInclusive,
                                          LocalDateTime toExclusive, int limit) {
        if (fromInclusive == null || toExclusive == null) {
            throw new IllegalArgumentException("time range must not be null");
        }
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    private List<AuditLog> readRows(PreparedStatement ps) throws SQLException {
        List<AuditLog> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private AuditLog mapRow(ResultSet rs) throws SQLException {
        Timestamp timestamp = rs.getTimestamp("timestamp");
        return new AuditLog(
                rs.getInt("log_id"),
                timestamp != null ? timestamp.toLocalDateTime() : null,
                rs.getString("username"),
                rs.getString("action"),
                rs.getString("entity_type"),
                (Integer) rs.getObject("entity_id"),
                rs.getString("details"),
                rs.getString("ip_address"),
                rs.getString("session_id")
        );
    }
}
