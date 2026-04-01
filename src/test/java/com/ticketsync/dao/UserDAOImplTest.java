package com.ticketsync.dao;

import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link UserDAOImpl}.
 *
 * <p><strong>Prerequisites:</strong> PostgreSQL running on localhost:5432,
 * database {@code ticketsync} exists, Flyway migrations applied.
 *
 * <p><strong>Enable tests:</strong>
 * <pre>
 * # PowerShell
 * $env:DB_TEST_ENABLED="true"; mvn test
 * </pre>
 *
 * <p>Each test inserts its own rows using a time-stamped username prefix and
 * cleans them up in {@link #tearDown()} to avoid collisions with the seeded
 * {@code admin} user.
 */
class UserDAOImplTest {

    private UserDAOImpl dao;
    private Connection conn;

    /** Unique prefix avoids collisions between test runs and the seeded admin row. */
    private final String prefix = "test_user_" + System.currentTimeMillis();

    /** Tracks any user_id inserted during a test so tearDown can clean up. */
    private int insertedUserId = -1;

    @BeforeEach
    void setUp() throws SQLException {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DB_TEST_ENABLED")),
                "Skipping DB test: DB_TEST_ENABLED is not set to 'true'");

        dao = new UserDAOImpl();
        conn = DatabaseConfig.getConnection();
        conn.setAutoCommit(true);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null) {
            try {
                if (insertedUserId > 0) {
                    dao.delete(conn, insertedUserId);
                }
            } finally {
                conn.close();
            }
        }
        insertedUserId = -1;
    }

    // -------------------------------------------------------------------------
    // insert
    // -------------------------------------------------------------------------

    /**
     * insert() should return a positive generated user_id and the user must be
     * retrievable via findById() immediately after.
     */
    @Test
    void insert_returnsPositiveIdAndUserIsRetrievable() throws SQLException {
        User user = buildUser(prefix + "_insert");

        insertedUserId = dao.insert(conn, user);
        assertTrue(insertedUserId > 0, "Generated user_id should be positive");

        Optional<User> found = dao.findById(conn, insertedUserId);
        assertTrue(found.isPresent(), "Inserted user should be retrievable by id");
        assertEquals(prefix + "_insert", found.get().getUsername());
    }

    /**
     * insert() should throw IllegalArgumentException for a null user.
     */
    @Test
    void insert_nullUser_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.insert(conn, null));
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    /**
     * findById() should return the correct user when the id exists.
     */
    @Test
    void findById_existingUser_returnsUser() throws SQLException {
        User user = buildUser(prefix + "_findById");
        insertedUserId = dao.insert(conn, user);

        Optional<User> found = dao.findById(conn, insertedUserId);
        assertTrue(found.isPresent());
        assertEquals(prefix + "_findById", found.get().getUsername());
        assertEquals("VENDOR", found.get().getRole());
    }

    /**
     * findById() should return Optional.empty() for an unknown id.
     */
    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<User> found = dao.findById(conn, Integer.MAX_VALUE);
        assertFalse(found.isPresent(), "Unknown id should return Optional.empty()");
    }

    /**
     * findById() should throw IllegalArgumentException for id &lt;= 0.
     */
    @Test
    void findById_invalidId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.findById(conn, 0));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findById(conn, -1));
    }

    // -------------------------------------------------------------------------
    // findByUsername
    // -------------------------------------------------------------------------

    /**
     * findByUsername() should return a user with a non-null passwordHash (needed
     * by AuthenticationService for BCrypt verification).
     */
    @Test
    void findByUsername_existingUser_returnsUserWithPasswordHash() throws SQLException {
        User user = buildUser(prefix + "_findByUsername");
        insertedUserId = dao.insert(conn, user);

        Optional<User> found = dao.findByUsername(conn, prefix + "_findByUsername");
        assertTrue(found.isPresent(), "User should be found by username");
        assertNotNull(found.get().getPasswordHash(),
                "passwordHash must not be null — required by AuthenticationService");
        assertEquals(prefix + "_findByUsername", found.get().getUsername());
    }

    /**
     * findByUsername() should return Optional.empty() for an unknown username.
     */
    @Test
    void findByUsername_unknownUsername_returnsEmpty() throws SQLException {
        Optional<User> found = dao.findByUsername(conn, "nonexistent_user_xyz");
        assertFalse(found.isPresent());
    }

    /**
     * findByUsername() should throw IllegalArgumentException for null or blank usernames.
     */
    @Test
    void findByUsername_nullOrBlank_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.findByUsername(conn, null));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findByUsername(conn, ""));
        assertThrows(IllegalArgumentException.class,
                () -> dao.findByUsername(conn, "   "));
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    /**
     * findAll() should return a non-null list that contains the inserted user.
     */
    @Test
    void findAll_containsInsertedUser() throws SQLException {
        User user = buildUser(prefix + "_findAll");
        insertedUserId = dao.insert(conn, user);

        List<User> all = dao.findAll(conn);
        assertNotNull(all, "findAll should never return null");
        assertTrue(all.stream().anyMatch(u -> u.getUsername().equals(prefix + "_findAll")),
                "findAll should contain the just-inserted user");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    /**
     * update() should persist the changed role; subsequent findById returns the
     * updated value.
     */
    @Test
    void update_changedRoleIsPersisted() throws SQLException {
        User user = buildUser(prefix + "_update");
        insertedUserId = dao.insert(conn, user);

        Optional<User> inserted = dao.findById(conn, insertedUserId);
        assertTrue(inserted.isPresent());

        User toUpdate = inserted.get();
        toUpdate.setRole("ADMIN");
        dao.update(conn, toUpdate);

        Optional<User> updated = dao.findById(conn, insertedUserId);
        assertTrue(updated.isPresent());
        assertEquals("ADMIN", updated.get().getRole(), "Role should have been updated to ADMIN");
    }

    /**
     * update() should throw IllegalArgumentException when user is null.
     */
    @Test
    void update_nullUser_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.update(conn, null));
    }

    /**
     * update() should throw IllegalArgumentException when userId &lt;= 0.
     */
    @Test
    void update_invalidUserId_throwsIllegalArgument() {
        User user = buildUser(prefix + "_invalid");
        user.setUserId(0);
        assertThrows(IllegalArgumentException.class,
                () -> dao.update(conn, user));
    }

    /**
     * update() should throw SQLException when no row matches the given userId
     * (e.g. user was deleted between lookup and update).
     */
    @Test
    void update_nonExistentUserId_throwsSQLException() {
        User ghost = buildUser(prefix + "_ghost");
        ghost.setUserId(Integer.MAX_VALUE);
        assertThrows(SQLException.class,
                () -> dao.update(conn, ghost));
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    /**
     * After delete(), findById() for the same id should return Optional.empty().
     */
    @Test
    void delete_subsequentFindByIdReturnsEmpty() throws SQLException {
        User user = buildUser(prefix + "_delete");
        insertedUserId = dao.insert(conn, user);

        dao.delete(conn, insertedUserId);
        Optional<User> found = dao.findById(conn, insertedUserId);
        assertFalse(found.isPresent(), "Deleted user should not be found");

        // Mark as already cleaned up so tearDown does not try again
        insertedUserId = -1;
    }

    /**
     * delete() should throw IllegalArgumentException for id &lt;= 0.
     */
    @Test
    void delete_invalidId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> dao.delete(conn, 0));
        assertThrows(IllegalArgumentException.class,
                () -> dao.delete(conn, -5));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a test {@link User} with the given username, a placeholder hash,
     * and the VENDOR role.
     *
     * @param username unique username for this test case
     * @return ready-to-insert User
     */
    private User buildUser(String username) {
        User user = new User();
        user.setUsername(username);
        // Placeholder BCrypt-shaped hash exactly 60 chars (matches VARCHAR(60) column)
        user.setPasswordHash("$2a$10$placeholderHashForTest0000000000000000000000000000000");
        user.setRole("VENDOR");
        return user;
    }
}
