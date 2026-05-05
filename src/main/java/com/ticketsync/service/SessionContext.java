package com.ticketsync.service;

import com.ticketsync.model.User;

import java.util.Optional;

/**
 * Contexto de sesión thread-local que proporciona estado de control de acceso basado en roles
 * para el usuario actualmente autenticado.
 *
 * <p>Esta clase no es instanciable. Todos los métodos son estáticos y operan sobre almacenamiento
 * {@link ThreadLocal} por hilo, haciéndolo seguro para usar en hilos de manejo de solicitudes
 * concurrentes sin sincronización.
 *
 * <p><strong>Patrón de uso:</strong>
 * <pre>{@code
 * // Tras un inicio de sesión exitoso:
 * SessionContext.setCurrentUser(user);
 *
 * // En un controlador o servicio:
 * Optional<User> user = SessionContext.getCurrentUser();
 * boolean isAdmin = SessionContext.hasRole("ADMIN");
 *
 * // Al cerrar sesión:
 * SessionContext.clearCurrentUser();
 * }</pre>
 *
 * <p><strong>Nota sobre pools de hilos:</strong> Llame siempre a {@link #clearCurrentUser()} cuando
 * la operación del usuario se complete. Usar {@code remove()} (no {@code set(null)})
 * garantiza que la entrada se elimine completamente de la tabla thread-local, previniendo fugas
 * de memoria en entornos de pool de hilos como los hilos de conexión de HikariCP.
 *
 * @see AuthenticationService
 */
public final class SessionContext {

    /**
     * Almacenamiento por hilo para el {@link User} actualmente autenticado.
     * Comienza vacío; se puebla por {@link #setCurrentUser(User)}.
     */
    private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

    /** Constructor privado — esta clase de utilidad no debe ser instanciada. */
    private SessionContext() {
    }

    /**
     * Almacena el {@link User} dado como el usuario autenticado actualmente para este hilo.
     *
     * @param user el usuario autenticado a almacenar; no debe ser {@code null}
     * @throws IllegalArgumentException si {@code user} es {@code null}
     */
    public static void setCurrentUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        CURRENT_USER.set(user);
    }

    /**
     * Devuelve el usuario actualmente autenticado para este hilo.
     *
     * @return un {@link Optional} con el {@link User} que tiene sesión iniciada,
     *         o {@link Optional#empty()} si ningún usuario está autenticado actualmente
     */
    public static Optional<User> getCurrentUser() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    /**
     * Elimina la sesión del usuario actual para este hilo.
     *
     * <p>Llama a {@link ThreadLocal#remove()} en lugar de {@code set(null)} para eliminar
     * completamente la entrada thread-local y evitar fugas de memoria en entornos de pool de hilos.
     */
    public static void clearCurrentUser() {
        CURRENT_USER.remove();
    }

    /**
     * Devuelve {@code true} si el usuario actualmente autenticado tiene el rol especificado.
     *
     * <p>La comparación de rol es insensible a mayúsculas (p.ej., {@code "admin"} coincide con {@code "ADMIN"}).
     * Devuelve {@code false} cuando ningún usuario tiene sesión iniciada o cuando {@code role} es {@code null}.
     *
     * @param role el nombre del rol a comprobar (p.ej., {@code "ADMIN"}, {@code "VENDOR"});
     *             si es {@code null}, devuelve {@code false} sin lanzar excepción
     * @return {@code true} si el rol del usuario actual coincide con {@code role}
     *         (insensible a mayúsculas); {@code false} en caso contrario
     */
    public static boolean hasRole(String role) {
        if (role == null) {
            return false;
        }
        return getCurrentUser()
                .map(User::getRole)
                .map(r -> r.equalsIgnoreCase(role))
                .orElse(false);
    }
}
