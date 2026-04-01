package com.ticketsync.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class for BCrypt password hashing and verification.
 *
 * <p>Uses jBCrypt with cost factor 12 as mandated by NFR-SEC01. All methods are
 * static; this class is not instantiable. No database interaction is performed here.
 *
 * <p><strong>Security note:</strong> Never log plaintext passwords or hash values.
 */
public final class PasswordHasher {

    /** Private constructor — non-instantiable utility class. */
    private PasswordHasher() {
    }

    /**
     * Hashes a plaintext password using BCrypt with cost factor 12.
     *
     * <p>Each invocation generates a new random salt, so two calls with the same
     * {@code plainPassword} will produce different hashes. The returned hash is
     * always exactly 60 characters long.
     *
     * @param plainPassword the plaintext password to hash; must not be {@code null} or blank
     * @return a 60-character BCrypt hash string
     * @throws IllegalArgumentException if {@code plainPassword} is {@code null} or blank
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("plainPassword must not be null or blank");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * @param attemptedPassword the plaintext password to verify; must not be {@code null}
     * @param storedHash        the BCrypt hash to verify against; must not be {@code null} or blank
     * @return {@code true} if {@code attemptedPassword} matches {@code storedHash},
     *         {@code false} otherwise (including blank or empty {@code attemptedPassword})
     * @throws IllegalArgumentException if {@code attemptedPassword} is {@code null},
     *                                  or if {@code storedHash} is {@code null} or blank
     */
    public static boolean verifyPassword(String attemptedPassword, String storedHash) {
        if (attemptedPassword == null) {
            throw new IllegalArgumentException("attemptedPassword must not be null");
        }
        if (storedHash == null || storedHash.isBlank()) {
            throw new IllegalArgumentException("storedHash must not be null or blank");
        }
        return BCrypt.checkpw(attemptedPassword, storedHash);
    }
}
