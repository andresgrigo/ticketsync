package com.ticketsync.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa un usuario del sistema con control de acceso basado en roles.
 * Mapea a la tabla de base de datos 'users'.
 * 
 * <h2>Roles</h2>
 * <ul>
 *   <li>ADMIN: Acceso completo al sistema (gestión de usuarios, configuración de eventos, plano de asientos, informes de ventas)</li>
 *   <li>VENDOR: Solo acceso al punto de venta (selección de eventos, compra de asientos, impresión de tickets)</li>
 * </ul>
 * 
 * <h2>Seguridad</h2>
 * El campo {@code passwordHash} almacena contraseñas hasheadas con BCrypt, nunca en texto plano.
 * El hash de contraseñas usa BCrypt (jBCrypt).
 * 
 * @see com.ticketsync.dao.UserDAO
 */
public class User {
    /** Clave primaria de la columna users.user_id. */
    private int userId;
    
    /** Nombre de usuario único para autenticación de inicio de sesión. */
    private String username;
    
    /** Contraseña hasheada con BCrypt (nunca en texto plano). */
    private String passwordHash;
    
    /** Rol del usuario: "ADMIN" o "VENDOR". */
    private String role;
    
    /** Marca de tiempo de cuando se creó la cuenta del usuario. */
    private LocalDateTime createdAt;
    
    /**
     * Constructor por defecto para mapeo JDBC.
     */
    public User() {
    }
    
    /**
     * Construye un User con todos los campos.
     * 
     * @param userId Clave primaria
     * @param username Nombre de usuario de inicio de sesión
     * @param passwordHash Contraseña hasheada con BCrypt
     * @param role Rol del usuario ("ADMIN" o "VENDOR")
     * @param createdAt Marca de tiempo de creación de la cuenta
     */
    public User(int userId, String username, String passwordHash, String role, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }
    
    // Getters y Setters

    /**
     * Devuelve el identificador del usuario.
     *
     * @return clave primaria generada por la base de datos
     */
    public int getUserId() {
        return userId;
    }

    /**
     * Establece el identificador del usuario.
     *
     * @param userId clave primaria generada por la base de datos
     */
    public void setUserId(int userId) {
        this.userId = userId;
    }

    /**
     * Devuelve el nombre de usuario de inicio de sesión.
     *
     * @return cadena de nombre de usuario único
     */
    public String getUsername() {
        return username;
    }

    /**
     * Establece el nombre de usuario de inicio de sesión.
     *
     * @param username nombre de usuario único; no debe ser null
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Devuelve el hash de contraseña BCrypt.
     *
     * @return cadena de contraseña hasheada con BCrypt; nunca en texto plano
     */
    public String getPasswordHash() {
        return passwordHash;
    }
    
    /**
     * Establece el hash de contraseña BCrypt.
     *
     * @param passwordHash contraseña hasheada con BCrypt; no debe ser null ni vacía
     */
    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isEmpty()) {
            throw new IllegalArgumentException("passwordHash cannot be null or empty");
        }
        this.passwordHash = passwordHash;
    }
    
    /**
     * Devuelve el rol del usuario.
     *
     * @return cadena de rol, ya sea {@code "ADMIN"} o {@code "VENDOR"}
     */
    public String getRole() {
        return role;
    }

    /**
     * Establece el rol del usuario.
     *
     * @param role cadena de rol; se espera que sea {@code "ADMIN"} o {@code "VENDOR"}
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Devuelve la marca de tiempo de creación de la cuenta.
     *
     * @return fecha y hora de cuando se creó la cuenta del usuario
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Establece la marca de tiempo de creación de la cuenta.
     *
     * @param createdAt fecha y hora de creación de la cuenta; puede ser null
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Métodos de Utilidad
    
    /**
     * Compara usuarios basado en la clave primaria.
     * 
     * @param o Objeto a comparar
     * @return true si tienen el mismo userId, false en caso contrario
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return userId == user.userId;
    }
    
    /**
     * Hash basado en la clave primaria.
     * 
     * @return Código hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
    
    /**
     * Representación en cadena para depuración (excluye el hash de contraseña por seguridad).
     * 
     * @return Representación en cadena
     */
    @Override
    public String toString() {
        return "User{userId=" + userId + ", username='" + username + "', role='" + role + "'}";
    }
}
