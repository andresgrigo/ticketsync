package com.ticketsync.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private static final String TEST_PASSWORD = "correctPassword";
    private static String storedHash;

    @BeforeAll
    static void setUpClass() {
        storedHash = PasswordHasher.hashPassword(TEST_PASSWORD);
    }

    // --- hashPassword ---

    @Test
    void hashPassword_returnsNonNull() {
        assertNotNull(storedHash);
    }

    @Test
    void hashPassword_returns60CharacterHash() {
        assertEquals(60, storedHash.length());
    }

    @Test
    void hashPassword_producesDifferentHashesForSameInput() {
        String hash1 = PasswordHasher.hashPassword(TEST_PASSWORD);
        String hash2 = PasswordHasher.hashPassword(TEST_PASSWORD);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashPassword_nullThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hashPassword(null));
    }

    @Test
    void hashPassword_emptyStringThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hashPassword(""));
    }

    // --- verifyPassword ---

    @Test
    void verifyPassword_returnsTrueForCorrectPassword() {
        assertTrue(PasswordHasher.verifyPassword(TEST_PASSWORD, storedHash));
    }

    @Test
    void verifyPassword_returnsFalseForWrongPassword() {
        assertFalse(PasswordHasher.verifyPassword("wrongPassword", storedHash));
    }

    @Test
    void verifyPassword_returnsFalseForBlankAttempt() {
        assertFalse(PasswordHasher.verifyPassword("   ", storedHash));
    }

    @Test
    void verifyPassword_nullAttemptThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.verifyPassword(null, storedHash));
    }

    @Test
    void verifyPassword_nullHashThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.verifyPassword(TEST_PASSWORD, null));
    }
}
