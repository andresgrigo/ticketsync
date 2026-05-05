package com.ticketsync.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Clase de utilidad para hash y verificación de contraseñas BCrypt.
 *
 * <p>Usa jBCrypt con factor de costo 12. Todos los métodos son
 * estáticos; esta clase no es instanciable. Aquí no se realiza ninguna interacción con la base de datos.
 *
 * <p><strong>Nota de seguridad:</strong> Nunca registrar en logs contraseñas en texto plano ni valores de hash.
 */
public final class PasswordHasher {

    /** Constructor privado — clase de utilidad no instanciable. */
    private PasswordHasher() {
    }

    /**
     * Hace hash de una contraseña en texto plano usando BCrypt con factor de costo 12.
     *
     * <p>Cada invocación genera una nueva sal aleatoria, por lo que dos llamadas con el mismo
     * {@code plainPassword} producirán hashes diferentes. El hash devuelto siempre
     * tiene exactamente 60 caracteres.
     *
     * @param plainPassword la contraseña en texto plano a hashear; no debe ser {@code null} ni estar en blanco
     * @return una cadena de hash BCrypt de 60 caracteres
     * @throws IllegalArgumentException si {@code plainPassword} es {@code null} o está en blanco
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("plainPassword must not be null or blank");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    /**
     * Verifica una contraseña en texto plano contra un hash BCrypt almacenado.
     *
     * @param attemptedPassword la contraseña en texto plano a verificar; no debe ser {@code null}
     * @param storedHash        el hash BCrypt contra el que verificar; no debe ser {@code null} ni estar en blanco
     * @return {@code true} si {@code attemptedPassword} coincide con {@code storedHash},
     *         {@code false} en caso contrario (incluyendo {@code attemptedPassword} en blanco o vacío)
     * @throws IllegalArgumentException si {@code attemptedPassword} es {@code null},
     *                                  o si {@code storedHash} es {@code null} o está en blanco
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
