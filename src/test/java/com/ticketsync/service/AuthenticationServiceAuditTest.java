package com.ticketsync.service;

import com.ticketsync.dao.UserDAO;
import com.ticketsync.model.AuditLog;
import com.ticketsync.model.User;
import com.ticketsync.util.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for authentication audit behavior without a live database.
 */
class AuthenticationServiceAuditTest {

    private StubUserDAO stubUserDAO;
    private CapturingAuditService stubAuditService;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        stubUserDAO = new StubUserDAO();
        stubAuditService = new CapturingAuditService();
        service = new AuthenticationService(stubUserDAO, stubAuditService, AuthenticationServiceAuditTest::noopConnection);
        SessionContext.clearCurrentUser();
    }

    @AfterEach
    void tearDown() {
        SessionContext.clearCurrentUser();
    }

    @Test
    void login_success_writesLoginSuccessAudit() throws SQLException {
        stubUserDAO.userToReturn = Optional.of(new User(7, "admin",
                PasswordHasher.hashPassword("admin123"), "ADMIN", null));

        Optional<User> result = service.login("admin", "admin123");

        assertTrue(result.isPresent());
        assertEquals(1, stubAuditService.persisted.size());
        assertEquals("LOGIN_SUCCESS", stubAuditService.persisted.getFirst().getAction());
        assertEquals("admin", stubAuditService.persisted.getFirst().getUsername());
    }

    @Test
    void login_failure_writesLoginFailureAuditWithoutSensitiveDetails() throws SQLException {
        stubUserDAO.userToReturn = Optional.empty();

        Optional<User> result = service.login("unknown", "secret");

        assertTrue(result.isEmpty());
        assertEquals(1, stubAuditService.persisted.size());
        AuditLog log = stubAuditService.persisted.getFirst();
        assertEquals("LOGIN_FAILURE", log.getAction());
        assertEquals("unknown", log.getUsername());
        assertFalse(log.getDetails().toLowerCase().contains("secret"));
        assertFalse(log.getDetails().toLowerCase().contains("password"));
    }

    private static Connection noopConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class) return 0;
                    return null;
                });
    }

    private static final class StubUserDAO implements UserDAO {
        Optional<User> userToReturn = Optional.empty();

        @Override
        public Optional<User> findById(Connection conn, int userId) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUsername(Connection conn, String username) {
            return userToReturn;
        }

        @Override
        public List<User> findAll(Connection conn) {
            return List.of();
        }

        @Override
        public int insert(Connection conn, User user) {
            return 0;
        }

        @Override
        public void update(Connection conn, User user) {
        }

        @Override
        public void delete(Connection conn, int userId) {
        }
    }

    private static final class CapturingAuditService extends AuditService {
        private final List<AuditLog> persisted = new ArrayList<>();

        @Override
        protected void persistAuditLog(AuditLog auditLog) {
            persisted.add(auditLog);
        }

        @Override
        protected List<AuditLog> queryAuditLogs(java.time.LocalDateTime fromInclusive,
                                                java.time.LocalDateTime toExclusive,
                                                String actionFilter, int limit) {
            return List.of();
        }
    }
}
