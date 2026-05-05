package com.ticketsync.service;

import com.ticketsync.dao.ZoneDAO;
import com.ticketsync.model.User;
import com.ticketsync.model.Zone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for price validation and creation flow in {@link ZoneService}.
 */
@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    private static final User ADMIN = new User(1, "admin1", "hash", "ADMIN", null);

    @Mock
    private ZoneDAO zoneDAO;

    @Mock
    private AuditService auditService;

    @Mock
    private ConnectionFactory connFactory;

    @Mock
    private Connection connection;

    private ZoneService service;

    @BeforeEach
    void setUp() {
        service = new ZoneService(zoneDAO, auditService, connFactory);
        SessionContext.setCurrentUser(ADMIN);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @Test
    void createZone_validPrice_insertsTrimmedZoneAndWritesAudit() throws SQLException {
        when(connFactory.get()).thenReturn(connection);
        when(zoneDAO.insert(eq(connection), any(Zone.class))).thenReturn(15);

        int zoneId = service.createZone(7, " VIP ", new BigDecimal("49.99"));

        assertEquals(15, zoneId);
        ArgumentCaptor<Zone> captor = ArgumentCaptor.forClass(Zone.class);
        verify(zoneDAO).insert(eq(connection), captor.capture());
        Zone inserted = captor.getValue();
        assertEquals(7, inserted.getEventId());
        assertEquals("VIP", inserted.getName());
        assertEquals(new BigDecimal("49.99"), inserted.getPrice());
        assertEquals(15, inserted.getZoneId());
        verify(auditService).logZoneCreated(inserted);
    }

    @Test
    void createZone_zeroPrice_throwsIllegalArgumentException() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> service.createZone(7, "Balcony", BigDecimal.ZERO));

        verify(zoneDAO, never()).insert(any(), any());
        verify(auditService, never()).logZoneCreated(any());
    }

    @Test
    void createZone_negativePrice_throwsIllegalArgumentException() throws SQLException {
        assertThrows(IllegalArgumentException.class,
                () -> service.createZone(7, "Balcony", new BigDecimal("-0.01")));

        verify(zoneDAO, never()).insert(any(), any());
        verify(auditService, never()).logZoneCreated(any());
    }

    @Test
    void createZone_withoutAdminRole_throwsSecurityExceptionBeforeDaoAccess() {
        SessionContext.clearCurrentUser();

        assertThrows(SecurityException.class,
                () -> service.createZone(7, "Balcony", new BigDecimal("19.99")));

        verifyNoInteractions(zoneDAO, auditService, connFactory);
    }

    @Test
    void updateZone_validZone_callsDaoAndWritesAudit() throws SQLException {
        when(connFactory.get()).thenReturn(connection);
        Zone zone = new Zone();
        zone.setZoneId(4);
        zone.setName("Floor");
        zone.setPrice(new BigDecimal("35.00"));

        service.updateZone(zone);

        verify(zoneDAO).update(connection, zone);
        verify(auditService).logZoneUpdated(zone);
    }

    @Test
    void deleteZone_existingZone_deletesAndWritesAudit() throws SQLException {
        when(connFactory.get()).thenReturn(connection);
        Zone existing = new Zone();
        existing.setZoneId(9);
        existing.setEventId(7);
        existing.setName("Balcony");
        existing.setPrice(new BigDecimal("22.50"));
        when(zoneDAO.findById(connection, 9)).thenReturn(Optional.of(existing));

        service.deleteZone(9);

        verify(zoneDAO).delete(connection, 9);
        verify(auditService).logZoneDeleted(existing);
    }

    @Test
    void getZonesByEvent_returnsDaoResults() throws SQLException {
        when(connFactory.get()).thenReturn(connection);
        Zone zone = new Zone();
        zone.setZoneId(3);
        zone.setName("VIP");
        when(zoneDAO.findByEventId(connection, 7)).thenReturn(List.of(zone));

        List<Zone> result = service.getZonesByEvent(7);

        assertEquals(List.of(zone), result);
        verify(zoneDAO).findByEventId(connection, 7);
    }

    @Test
    void countSeatsForZone_returnsCountFromQuery() throws SQLException {
        when(connFactory.get()).thenReturn(connection);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT COUNT(*) FROM seats WHERE zone_id = ?")).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(12);

        int count = service.countSeatsForZone(5);

        assertEquals(12, count);
        verify(ps).setInt(1, 5);
    }

    @Test
    void defaultConstructor_createsServiceWithoutImmediateIo() {
        ZoneService defaultService = new ZoneService();

        assertNotNull(defaultService);
    }

    @Test
    void daoOnlyConstructor_createsServiceWithoutImmediateIo() {
        ZoneService zoneService = new ZoneService(zoneDAO, connFactory);

        assertNotNull(zoneService);
    }
}
