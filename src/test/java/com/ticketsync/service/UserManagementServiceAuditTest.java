package com.ticketsync.service;

import com.ticketsync.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for admin mutation audit logging in {@link UserManagementService}.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceAuditTest {

    @Mock
    private com.ticketsync.dao.UserDAO userDAO;

    @Mock
    private AuditService auditService;

    @Mock
    private ConnectionFactory connFactory;

    @Mock
    private Connection connection;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(userDAO, auditService, connFactory);
    }

    @Test
    void createUpdateDeleteUser_writeAuditEntries() throws Exception {
        when(connFactory.get()).thenReturn(connection);
        when(userDAO.insert(eq(connection), any(User.class))).thenReturn(9);
        User existing = new User(9, "vendor9", "hash", "VENDOR", LocalDateTime.now());

        int userId = service.createUser("vendor9", "secret123", "VENDOR", "admin1");
        service.updateUserRole(existing, "ADMIN", "admin1");
        service.deleteUser(existing.getUserId(), existing.getUsername(), "admin1");

        assertEquals(9, userId);
        ArgumentCaptor<User> createdUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userDAO).insert(eq(connection), createdUserCaptor.capture());
        assertEquals("vendor9", createdUserCaptor.getValue().getUsername());
        assertEquals("VENDOR", createdUserCaptor.getValue().getRole());

        verify(auditService).logUserCreated("admin1", 9, "vendor9", "VENDOR");
        verify(auditService).logUserRoleUpdated("admin1", existing, "ADMIN");
        verify(auditService).logUserDeleted("admin1", existing.getUserId(), existing.getUsername());
    }

    @Test
    void getAllUsers_returnsDaoResults() throws Exception {
        User vendor = new User(4, "vendor4", "hash", "VENDOR", LocalDateTime.now());
        when(connFactory.get()).thenReturn(connection);
        when(userDAO.findAll(connection)).thenReturn(List.of(vendor));

        List<User> result = service.getAllUsers();

        assertEquals(List.of(vendor), result);
        verify(userDAO).findAll(connection);
    }

    @Test
    void usernameExists_returnsTrueWhenDaoFindsUser() throws Exception {
        User vendor = new User(4, "vendor4", "hash", "VENDOR", LocalDateTime.now());
        when(connFactory.get()).thenReturn(connection);
        when(userDAO.findByUsername(connection, "vendor4")).thenReturn(Optional.of(vendor));

        boolean exists = service.usernameExists("vendor4");

        assertEquals(true, exists);
        verify(userDAO).findByUsername(connection, "vendor4");
    }

    @Test
    void createUser_invalidRole_throwsIllegalArgumentExceptionBeforeDaoAccess() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.createUser("vendor4", "secret123", "SUPPORT", "admin1"));

        assertEquals("Invalid role: SUPPORT. Must be ADMIN or VENDOR.", ex.getMessage());
        verifyNoInteractions(connFactory, userDAO, auditService);
    }

    @Test
    void createUser_duplicateKeyWrapsSQLExceptionWithFriendlyMessage() throws Exception {
        when(connFactory.get()).thenReturn(connection);
        when(userDAO.insert(eq(connection), any(User.class))).thenThrow(new SQLException("duplicate key value"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createUser("vendor4", "secret123", "VENDOR", "admin1"));

        assertEquals("Username 'vendor4' is already taken.", ex.getMessage());
    }

    @Test
    void defaultConstructor_createsServiceWithoutImmediateIo() {
        UserManagementService defaultService = new UserManagementService();

        assertNotNull(defaultService);
    }
}
