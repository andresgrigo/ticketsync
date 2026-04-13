package com.ticketsync.service;

import com.ticketsync.model.AuditLog;
import com.ticketsync.model.Sale;
import com.ticketsync.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuditService}.
 *
 * <p>These tests use a capturing subclass instead of a live database so the
 * audit seam can be verified without JDBC setup.
 */
class AuditServiceTest {

    private static final User ADMIN = new User(1, "admin1", "hash", "ADMIN", null);
    private static final User VENDOR = new User(2, "vendor1", "hash", "VENDOR", null);

    private CapturingAuditService service;

    @BeforeEach
    void setUp() {
        service = new CapturingAuditService();
        SessionContext.clearCurrentUser();
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @Test
    void logPurchaseCompleted_usesSessionUsernameAndJsonDetails() {
        SessionContext.setCurrentUser(VENDOR);
        Sale sale = new Sale(77, 10, 2, new BigDecimal("42.50"),
                LocalDateTime.of(2026, 4, 11, 15, 0), "Booth-9");

        service.logPurchaseCompleted(sale, List.of(5, 6));

        assertEquals(1, service.persisted.size());
        AuditLog log = service.persisted.getFirst();
        assertEquals("vendor1", log.getUsername());
        assertEquals("PURCHASE_SEATS", log.getAction());
        assertEquals("SALE", log.getEntityType());
        assertEquals(77, log.getEntityId());
        assertTrue(log.getDetails().contains("\"seatIds\":[5,6]"));
        assertTrue(log.getDetails().contains("\"total\":42.50"));
        assertTrue(log.getDetails().contains("\"boothId\":\"Booth-9\""));
    }

    @Test
    void logLoginFailure_doesNotCaptureSecrets() {
        service.logLoginFailure("vendor1");

        assertEquals(1, service.persisted.size());
        AuditLog log = service.persisted.getFirst();
        assertEquals("LOGIN_FAILURE", log.getAction());
        assertEquals("vendor1", log.getUsername());
        assertFalse(log.getDetails().toLowerCase().contains("password"));
        assertFalse(log.getDetails().toLowerCase().contains("hash"));
        assertFalse(log.getDetails().toLowerCase().contains("token"));
        assertFalse(log.getDetails().toLowerCase().contains("key"));
    }

    @Test
    void getAuditLogs_requiresAdminRole() {
        SessionContext.setCurrentUser(VENDOR);

        assertThrows(SecurityException.class,
                () -> service.getAuditLogs(LocalDateTime.now().minusDays(7), LocalDateTime.now(), null, null, 50));
    }

    @Test
    void getAuditLogs_appliesSecondaryUsernameFilterAfterPrimaryQuery() throws SQLException {
        SessionContext.setCurrentUser(ADMIN);
        service.queryResults = List.of(
                audit("admin1", "EVENT_CREATED"),
                audit("vendor1", "PURCHASE_SEATS"),
                audit("admin1", "LOGIN_SUCCESS")
        );

        List<AuditLog> result = service.getAuditLogs(
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now(),
                null,
                "admin1",
                100
        );

        assertEquals(2, result.size());
        assertEquals(List.of("admin1", "admin1"), result.stream().map(AuditLog::getUsername).toList());
    }

    private static AuditLog audit(String username, String action) {
        AuditLog log = new AuditLog();
        log.setLogId(username.hashCode() + action.hashCode());
        log.setTimestamp(LocalDateTime.now());
        log.setUsername(username);
        log.setAction(action);
        log.setEntityType("TEST");
        log.setEntityId(1);
        log.setDetails("{\"ok\":true}");
        return log;
    }

    private static final class CapturingAuditService extends AuditService {
        private final List<AuditLog> persisted = new ArrayList<>();
        private List<AuditLog> queryResults = List.of();

        @Override
        protected void persistAuditLog(AuditLog auditLog) {
            persisted.add(auditLog);
        }

        @Override
        protected List<AuditLog> queryAuditLogs(LocalDateTime fromInclusive, LocalDateTime toExclusive,
                                                String actionFilter, int limit) {
            return queryResults;
        }
    }
}
