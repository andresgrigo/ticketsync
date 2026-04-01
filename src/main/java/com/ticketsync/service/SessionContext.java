package com.ticketsync.service;

import com.ticketsync.model.User;

import java.util.Optional;

/**
 * Thread-local session context providing role-based access control state for the
 * currently authenticated user.
 *
 * <p>This class is non-instantiable. All methods are static and operate on a
 * per-thread {@link ThreadLocal} storage, making it safe to use across
 * concurrent request-handling threads without synchronisation.
 *
 * <p><strong>Usage pattern:</strong>
 * <pre>{@code
 * // After successful login:
 * SessionContext.setCurrentUser(user);
 *
 * // In a controller or service:
 * Optional<User> user = SessionContext.getCurrentUser();
 * boolean isAdmin = SessionContext.hasRole("ADMIN");
 *
 * // On logout:
 * SessionContext.clearCurrentUser();
 * }</pre>
 *
 * <p><strong>Thread-pool note:</strong> Always call {@link #clearCurrentUser()} when
 * the user's operation is complete. Using {@code remove()} (not {@code set(null)})
 * ensures the entry is fully removed from the thread-local table, preventing memory
 * leaks in thread-pool environments such as HikariCP's connection threads.
 *
 * @see AuthenticationService
 */
public final class SessionContext {

    /**
     * Per-thread storage for the currently authenticated {@link User}.
     * Starts empty; populated by {@link #setCurrentUser(User)}.
     */
    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    /** Private constructor — this utility class must not be instantiated. */
    private SessionContext() {
    }

    /**
     * Stores the given {@link User} as the currently authenticated user for this thread.
     *
     * @param user the authenticated user to store; must not be {@code null}
     * @throws IllegalArgumentException if {@code user} is {@code null}
     */
    public static void setCurrentUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        CURRENT_USER.set(user);
    }

    /**
     * Returns the currently authenticated user for this thread.
     *
     * @return an {@link Optional} containing the logged-in {@link User},
     *         or {@link Optional#empty()} if no user is currently authenticated
     */
    public static Optional<User> getCurrentUser() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    /**
     * Removes the current user session for this thread.
     *
     * <p>Calls {@link ThreadLocal#remove()} rather than {@code set(null)} to fully
     * remove the thread-local entry and avoid memory leaks in thread-pool environments.
     */
    public static void clearCurrentUser() {
        CURRENT_USER.remove();
    }

    /**
     * Returns {@code true} if the currently authenticated user has the specified role.
     *
     * <p>Role comparison is case-insensitive (e.g., {@code "admin"} matches {@code "ADMIN"}).
     * Returns {@code false} when no user is logged in.
     *
     * @param role the role name to check (e.g., {@code "ADMIN"}, {@code "VENDOR"})
     * @return {@code true} if the current user's role matches {@code role}
     *         (case-insensitive); {@code false} otherwise
     */
    public static boolean hasRole(String role) {
        return getCurrentUser()
                .map(User::getRole)
                .map(r -> r.equalsIgnoreCase(role))
                .orElse(false);
    }
}
