package com.ticketsync.service;

import com.ticketsync.dao.UserDAO;
import com.ticketsync.model.AuditLog;
import com.ticketsync.model.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for admin mutation audit logging in {@link UserManagementService}.
 */
class UserManagementServiceAuditTest {

    @Test
    void createUpdateDeleteUser_writeAuditEntries() throws Exception {
        StubUserDAO stubUserDAO = new StubUserDAO();
        CapturingAuditService auditService = new CapturingAuditService();
        UserManagementService service = new UserManagementService(stubUserDAO, auditService,
                UserManagementServiceAuditTest::noopConnection);
        User existing = new User(9, "vendor9", "hash", "VENDOR", LocalDateTime.now());

        int userId = service.createUser("vendor9", "secret123", "VENDOR", "admin1");
        service.updateUserRole(existing, "ADMIN", "admin1");
        service.deleteUser(existing.getUserId(), existing.getUsername(), "admin1");

        assertEquals(9, userId);
        assertEquals(List.of("USER_CREATED", "USER_ROLE_UPDATED", "USER_DELETED"),
                auditService.persisted.stream().map(AuditLog::getAction).toList());
        assertTrue(auditService.persisted.stream().allMatch(log -> "admin1".equals(log.getUsername())));
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
        int nextInsertId = 9;

        @Override
        public Optional<User> findById(Connection conn, int userId) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByUsername(Connection conn, String username) {
            return Optional.empty();
        }

        @Override
        public List<User> findAll(Connection conn) {
            return List.of();
        }

        @Override
        public int insert(Connection conn, User user) {
            return nextInsertId;
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
