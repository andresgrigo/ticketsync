package com.ticketsync.dao;

import com.ticketsync.model.User;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for User database operations.
 * 
 * <h2>Transaction Participation</h2>
 * All methods accept a Connection parameter to participate in service-layer transactions.
 * Implementations use PreparedStatement for SQL injection prevention.
 * 
 * <h2>Database Mapping</h2>
 * <ul>
 *   <li>Database Table: users</li>
 *   <li>Primary Key: user_id (SERIAL)</li>
 *   <li>Unique Constraint: username</li>
 * </ul>
 * 
 * <h2>Security</h2>
 * Passwords are stored as BCrypt hashes, never plaintext.
 * Passwords are stored as BCrypt hashes using jBCrypt.
 * 
 * @see com.ticketsync.model.User
 */
public interface UserDAO {
    
    /**
     * Finds a user by primary key.
     * 
     * @param conn Active database connection
     * @param userId Primary key of user to retrieve
     * @return Optional containing User if found, empty otherwise
     * @throws SQLException if database access error occurs
     * @throws IllegalArgumentException if userId is zero or negative
     */
    Optional<User> findById(Connection conn, int userId) throws SQLException;
    
    /**
     * Finds a user by username for authentication.
     * Used in login workflow to validate credentials.
     * 
     * @param conn Active database connection
     * @param username Username to search for
     * @return Optional containing User if found, empty otherwise
     * @throws SQLException if database access error occurs
     * @throws IllegalArgumentException if username is null or empty
     */
    Optional<User> findByUsername(Connection conn, String username) throws SQLException;
    
    /**
     * Retrieves all users in the system.
     * Used in admin user management interface.
     * 
     * @param conn Active database connection
     * @return List of all users, empty list if none exist
     * @throws SQLException if database access error occurs
     */
    List<User> findAll(Connection conn) throws SQLException;
    
    /**
     * Inserts a new user into the database.
     * Password must be BCrypt hashed before calling this method.
     * 
     * @param conn Active database connection
     * @param user User object to insert (userId field is ignored; database generates the key).
     *        The {@code passwordHash} field must contain a BCrypt hash, never plaintext.
     * @return Generated user_id from database
     * @throws SQLException if database access error or constraint violation occurs
     * @throws IllegalArgumentException if user is null
     */
    int insert(Connection conn, User user) throws SQLException;
    
    /**
     * Updates an existing user's information.
     * Used in admin user management to modify roles or reset passwords.
     * 
     * @param conn Active database connection
     * @param user User object with updated fields (userId must be set)
     * @throws SQLException if database access error or user not found
     */
    void update(Connection conn, User user) throws SQLException;
    
    /**
     * Deletes a user from the database.
     * Used in admin user management interface.
     * 
     * <p><strong>Note:</strong> Deletion may fail due to foreign key constraints
     * if user has created events or sales records.
     * 
     * @param conn Active database connection
     * @param userId Primary key of user to delete
     * @throws SQLException if database access error or constraint violation occurs
     */
    void delete(Connection conn, int userId) throws SQLException;
}
