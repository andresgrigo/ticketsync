package com.ticketsync.dao;

import com.ticketsync.model.AuditLog;
import com.ticketsync.util.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link AuditDAOImpl}.
 */
class AuditDAOImplTest {

    private AuditDAOImpl dao;
    private Connection conn;
    private String usernamePrefix;
    private final List<Integer> insertedLogIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");
        dao = new AuditDAOImpl();
        conn = DatabaseConfig.getConnection();
        conn.setAutoCommit(true);
        usernamePrefix = "audit_test_" + System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null) {
            try {
                for (int logId : insertedLogIds) {
                    deleteAuditLog(conn, logId);
                }
                deleteByUsernamePrefix(conn, usernamePrefix);
            } finally {
                conn.close();
            }
        }
        insertedLogIds.clear();
    }

    @Test
    void insert_persistsJsonbDetailsAndReturnsGeneratedId() throws SQLException {
        AuditLog log = auditLog(usernamePrefix + "_insert", "PURCHASE_SEATS",
                LocalDateTime.now().withNano(0),
                "{\"seatIds\":[5,6],\"total\":42.50,\"boothId\":\"Booth-9\"}");

        int logId = dao.insert(conn, log);
        insertedLogIds.add(logId);

        assertTrue(logId > 0);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT details->>'boothId', details->>'total', jsonb_typeof(details->'seatIds') "
                        + "FROM audit_log WHERE log_id = ?")) {
            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Booth-9", rs.getString(1));
                assertEquals("42.50", rs.getString(2));
                assertEquals("array", rs.getString(3));
            }
        }
    }

    @Test
    void findRecentByAction_returnsNewestRowsAndIndexedPlan() throws SQLException {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int oldestId = dao.insert(conn, auditLog(usernamePrefix + "_action", "LOGIN_FAILURE",
                now.minusMinutes(20), "{\"outcome\":\"failure\"}"));
        int newestId = dao.insert(conn, auditLog(usernamePrefix + "_action", "LOGIN_FAILURE",
                now.minusMinutes(5), "{\"outcome\":\"failure\"}"));
        int otherId = dao.insert(conn, auditLog(usernamePrefix + "_action", "LOGIN_SUCCESS",
                now.minusMinutes(2), "{\"outcome\":\"success\"}"));
        insertedLogIds.add(oldestId);
        insertedLogIds.add(newestId);
        insertedLogIds.add(otherId);

        List<AuditLog> result = dao.findRecentByAction(
                conn,
                now.minusDays(1),
                now.plusMinutes(1),
                "LOGIN_FAILURE",
                10
        );

        List<AuditLog> testRows = result.stream()
                .filter(log -> log.getLogId() == oldestId || log.getLogId() == newestId || log.getLogId() == otherId)
                .toList();
        assertEquals(2, testRows.size());
        assertEquals(newestId, testRows.getFirst().getLogId());
        assertEquals(oldestId, testRows.get(1).getLogId());
        assertFalse(testRows.stream().anyMatch(log -> "LOGIN_SUCCESS".equals(log.getAction())));

        try (PreparedStatement disableSeqScan = conn.prepareStatement("SET enable_seqscan = off")) {
            disableSeqScan.execute();
        }
        try (PreparedStatement explain = conn.prepareStatement(
                "EXPLAIN (COSTS OFF) "
                        + "SELECT log_id, timestamp, username, action, entity_type, entity_id, details, ip_address, session_id "
                        + "FROM audit_log "
                        + "WHERE timestamp >= ? AND timestamp < ? AND action = ? "
                        + "ORDER BY timestamp DESC, action ASC "
                        + "LIMIT ?")) {
            explain.setTimestamp(1, Timestamp.valueOf(now.minusDays(1)));
            explain.setTimestamp(2, Timestamp.valueOf(now.plusMinutes(1)));
            explain.setString(3, "LOGIN_FAILURE");
            explain.setInt(4, 10);
            List<String> planLines = new ArrayList<>();
            try (ResultSet rs = explain.executeQuery()) {
                while (rs.next()) {
                    planLines.add(rs.getString(1));
                }
            }
            assertTrue(planLines.stream().anyMatch(line -> line.contains("idx_audit_timestamp_action")),
                    "Query plan should use idx_audit_timestamp_action");
        } finally {
            try (PreparedStatement resetSeqScan = conn.prepareStatement("SET enable_seqscan = on")) {
                resetSeqScan.execute();
            }
        }
    }

    private static AuditLog auditLog(String username, String action,
                                     LocalDateTime timestamp, String details) {
        AuditLog log = new AuditLog();
        log.setTimestamp(timestamp);
        log.setUsername(username);
        log.setAction(action);
        log.setEntityType("TEST");
        log.setEntityId(1);
        log.setDetails(details);
        return log;
    }

    private static void deleteAuditLog(Connection conn, int logId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM audit_log WHERE log_id = ?")) {
            ps.setInt(1, logId);
            ps.executeUpdate();
        }
    }

    private static void deleteByUsernamePrefix(Connection conn, String prefix) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM audit_log WHERE username LIKE ?")) {
            ps.setString(1, prefix + "%");
            ps.executeUpdate();
        }
    }
}
