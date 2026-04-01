package com.ticketsync.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a system user with role-based access control.
 * Maps to the 'users' database table.
 * 
 * <h2>Roles</h2>
 * <ul>
 *   <li>ADMIN: Full system access (user management, event config, seating layout, sales reports)</li>
 *   <li>VENDOR: Point-of-sale access only (event selection, seat purchase, ticket printing)</li>
 * </ul>
 * 
 * <h2>Security</h2>
 * The {@code passwordHash} field stores BCrypt hashed passwords, never plaintext.
 * Password hashing uses BCrypt (jBCrypt).
 * 
 * @see com.ticketsync.dao.UserDAO
 */
public class User {
    /** Primary key from users.user_id column. */
    private int userId;
    
    /** Unique username for login authentication. */
    private String username;
    
    /** BCrypt hashed password (never plaintext). */
    private String passwordHash;
    
    /** User role: "ADMIN" or "VENDOR". */
    private String role;
    
    /** Timestamp when user account was created. */
    private LocalDateTime createdAt;
    
    /**
     * Default constructor for JDBC mapping.
     */
    public User() {
    }
    
    /**
     * Constructs a User with all fields.
     * 
     * @param userId Primary key
     * @param username Login username
     * @param passwordHash BCrypt hashed password
     * @param role User role ("ADMIN" or "VENDOR")
     * @param createdAt Account creation timestamp
     */
    public User(int userId, String username, String passwordHash, String role, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }
    
    // Getters and Setters
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isEmpty()) {
            throw new IllegalArgumentException("passwordHash cannot be null or empty");
        }
        this.passwordHash = passwordHash;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Utility Methods
    
    /**
     * Compares users based on primary key.
     * 
     * @param o Object to compare
     * @return true if same userId, false otherwise
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
     * Hash based on primary key.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
    
    /**
     * String representation for debugging (excludes password hash for security).
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "User{userId=" + userId + ", username='" + username + "', role='" + role + "'}";
    }
}
