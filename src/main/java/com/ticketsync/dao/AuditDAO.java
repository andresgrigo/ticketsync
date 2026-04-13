package com.ticketsync.dao;

import com.ticketsync.model.AuditLog;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Access Object for {@code audit_log} operations.
 */
public interface AuditDAO {

    /**
     * Inserts an audit row and returns the generated {@code log_id}.
     *
     * @param conn active database connection
     * @param auditLog audit record to insert
     * @return generated {@code log_id}
     * @throws SQLException if the insert fails
     */
    int insert(Connection conn, AuditLog auditLog) throws SQLException;

    /**
     * Returns recent audit rows on the indexed timestamp/action path.
     *
     * @param conn active database connection
     * @param fromInclusive lower timestamp bound
     * @param toExclusive upper timestamp bound
     * @param limit max rows to return
     * @return matching audit rows ordered by timestamp descending then action ascending
     * @throws SQLException if the query fails
     */
    List<AuditLog> findRecent(Connection conn, LocalDateTime fromInclusive,
                              LocalDateTime toExclusive, int limit) throws SQLException;

    /**
     * Returns recent audit rows filtered to one action on the indexed timestamp/action path.
     *
     * @param conn active database connection
     * @param fromInclusive lower timestamp bound
     * @param toExclusive upper timestamp bound
     * @param action action filter
     * @param limit max rows to return
     * @return matching audit rows ordered by timestamp descending then action ascending
     * @throws SQLException if the query fails
     */
    List<AuditLog> findRecentByAction(Connection conn, LocalDateTime fromInclusive,
                                      LocalDateTime toExclusive, String action,
                                      int limit) throws SQLException;
}
