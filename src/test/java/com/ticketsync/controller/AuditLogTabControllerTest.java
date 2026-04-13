package com.ticketsync.controller;

import com.ticketsync.model.AuditLog;
import com.ticketsync.model.User;
import com.ticketsync.service.AuditService;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the read-only admin audit tab controller.
 */
class AuditLogTabControllerTest {

    @Test
    void setAdminUser_nonAdmin_throwsSecurityException() {
        AuditLogTabController controller = new AuditLogTabController(new StubAuditService());

        assertThrows(SecurityException.class,
                () -> controller.setAdminUser(new User(2, "vendor1", "hash", "VENDOR", null)));
    }

    @Test
    void loadAuditEntries_emptyResults_buildsEmptyStateMessage() throws SQLException {
        StubAuditService auditService = new StubAuditService();
        AuditLogTabController controller = new AuditLogTabController(auditService);
        controller.setAdminUser(new User(1, "admin1", "hash", "ADMIN", null));

        List<AuditLog> result = controller.loadAuditEntries(
                AuditLogTabController.TimeWindow.LAST_7_DAYS,
                null,
                null
        );

        assertTrue(result.isEmpty());
        assertEquals("No audit entries found.", controller.buildStatusMessage(result));
    }

    @Test
    void loadAuditEntries_passesFiltersToService() throws SQLException {
        StubAuditService auditService = new StubAuditService();
        AuditLogTabController controller = new AuditLogTabController(auditService);
        controller.setAdminUser(new User(1, "admin1", "hash", "ADMIN", null));

        controller.loadAuditEntries(
                AuditLogTabController.TimeWindow.LAST_24_HOURS,
                "LOGIN_FAILURE",
                "admin1"
        );

        assertEquals("LOGIN_FAILURE", auditService.lastActionFilter);
        assertEquals("admin1", auditService.lastUsernameFilter);
        assertEquals(200, auditService.lastLimit);
        assertTrue(auditService.lastFrom.isBefore(auditService.lastTo));
    }

    private static final class StubAuditService extends AuditService {
        private LocalDateTime lastFrom;
        private LocalDateTime lastTo;
        private String lastActionFilter;
        private String lastUsernameFilter;
        private int lastLimit;

        @Override
        public List<AuditLog> getAuditLogs(LocalDateTime fromInclusive, LocalDateTime toExclusive,
                                           String actionFilter, String usernameFilter, int limit) {
            lastFrom = fromInclusive;
            lastTo = toExclusive;
            lastActionFilter = actionFilter;
            lastUsernameFilter = usernameFilter;
            lastLimit = limit;
            return List.of();
        }
    }
}
