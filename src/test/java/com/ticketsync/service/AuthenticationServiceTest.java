package com.ticketsync.service;

import com.ticketsync.dao.UserDAO;
import com.ticketsync.model.User;
import com.ticketsync.util.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthenticationService} using mocked DAO and audit seams.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private AuditService auditService;

    @Mock
    private ConnectionFactory connFactory;

    @Mock
    private Connection connection;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(userDAO, auditService, connFactory);
        SessionContext.clearCurrentUser();
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @Test
    void login_withValidCredentials_returnsUserAndPopulatesSession() throws SQLException {
        User user = new User(1, "admin", PasswordHasher.hashPassword("admin123"), "ADMIN", null);
        when(connFactory.get()).thenReturn(connection);
        when(userDAO.findByUsername(connection, "admin")).thenReturn(Optional.of(user));

        Optional<User> result = service.login("admin", "admin123");

        assertTrue(result.isPresent());
        assertSame(user, result.get());
        assertEquals(Optional.of(user), SessionContext.getCurrentUser());
        verify(auditService).logLoginSuccess("admin");
        verify(auditService, never()).logLoginFailure(anyString());
    }

    @Test
    void login_withWrongPassword_returnsEmptyAndWritesFailureAudit() throws SQLException {
        User user = new User(1, "admin", PasswordHasher.hashPassword("admin123"), "ADMIN", null);
        when(connFactory.get()).thenReturn(connection);
        when(userDAO.findByUsername(connection, "admin")).thenReturn(Optional.of(user));

        Optional<User> result = service.login("admin", "wrongpass");

        assertTrue(result.isEmpty());
        assertTrue(SessionContext.getCurrentUser().isEmpty());
        verify(auditService).logLoginFailure("admin");
        verify(auditService, never()).logLoginSuccess(anyString());
    }

    @Test
    void login_withUnknownUsername_returnsEmptyAndWritesFailureAudit() throws SQLException {
        when(connFactory.get()).thenReturn(connection);
        when(userDAO.findByUsername(connection, "ghost")).thenReturn(Optional.empty());

        Optional<User> result = service.login("ghost", "irrelevant");

        assertTrue(result.isEmpty());
        assertTrue(SessionContext.getCurrentUser().isEmpty());
        verify(auditService).logLoginFailure("ghost");
        verify(auditService, never()).logLoginSuccess(anyString());
    }

    @Test
    void logout_clearsSessionContext() {
        SessionContext.setCurrentUser(new User(9, "vendor1", "hash", "VENDOR", null));

        service.logout();

        assertTrue(SessionContext.getCurrentUser().isEmpty());
    }
}
