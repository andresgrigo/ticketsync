/**
 * Domain model classes representing business entities.
 * 
 * <h2>Purpose</h2>
 * Pure Java POJOs mapping to database table schemas. Model classes have NO business logic,
 * NO database access, and NO JavaFX dependencies.
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Each class maps to a database table (User maps to users, Event maps to events, etc.)</li>
 *   <li>Fields match database column names (camelCase in Java, snake_case in SQL)</li>
 *   <li>Immutability not enforced (JavaFX requires mutable beans for TableView binding)</li>
 *   <li>Primary keys are int types matching PostgreSQL SERIAL columns</li>
 *   <li>All classes implement equals(), hashCode(), and toString()</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * Create a new user entity, set properties, and persist via DAO.
 * 
 * @see com.ticketsync.dao
 * @see com.ticketsync.viewmodel
 */
package com.ticketsync.model;
