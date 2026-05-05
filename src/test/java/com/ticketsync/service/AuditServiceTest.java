package com.ticketsync.service;

import com.ticketsync.dao.AuditDAO;
import com.ticketsync.model.AuditLog;
import com.ticketsync.model.Sale;
import com.ticketsync.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditService}.
 *
 * <p>These tests use mocked DAO/connection seams instead of a live database.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final User ADMIN = new User(1, "admin1", "hash", "ADMIN", null);
    private static final User VENDOR = new User(2, "vendor1", "hash", "VENDOR", null);

    @Mock
    private AuditDAO auditDAO;

    @Mock
    private ConnectionFactory connFactory;

    @Mock
    private Connection connection;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditDAO, connFactory);
        SessionContext.clearCurrentUser();
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @Test
    void logPurchaseCompleted_usesSessionUsernameAndJsonDetails() throws SQLException {
        SessionContext.setCurrentUser(VENDOR);
        when(connFactory.get()).thenReturn(connection);
        Sale sale = new Sale(77, 10, 2, new BigDecimal("42.50"),
                LocalDateTime.of(2026, 4, 11, 15, 0), "Booth-9");

        service.logPurchaseCompleted(sale, List.of(5, 6));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditDAO).insert(eq(connection), captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("vendor1", log.getUsername());
        assertEquals("PURCHASE_SEATS", log.getAction());
        assertEquals("SALE", log.getEntityType());
        assertEquals(77, log.getEntityId());
        assertTrue(log.getDetails().contains("\"seatIds\":[5,6]"));
        assertTrue(log.getDetails().contains("\"total\":42.50"));
        assertTrue(log.getDetails().contains("\"boothId\":\"Booth-9\""));
    }

    @Test
    void logLoginFailure_doesNotCaptureSecrets() throws SQLException {
        when(connFactory.get()).thenReturn(connection);
        service.logLoginFailure("vendor1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditDAO).insert(eq(connection), captor.capture());
        AuditLog log = captor.getValue();
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
        when(connFactory.get()).thenReturn(connection);
        when(auditDAO.findRecent(eq(connection), any(), any(), eq(100))).thenReturn(List.of(
                audit("admin1", "EVENT_CREATED"),
                audit("vendor1", "PURCHASE_SEATS"),
                audit("admin1", "LOGIN_SUCCESS")
        ));

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
}
