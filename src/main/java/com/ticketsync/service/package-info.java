/**
 * Business logic and transaction management layer.
 * 
 * <h2>Purpose</h2>
 * Service layer orchestrates DAO operations within transactions, enforces business rules,
 * and provides a clean API for ViewModels to execute domain operations.
 * 
 * <h2>Architectural Responsibilities</h2>
 * <ul>
 *   <li>Transaction boundary management (Connection lifecycle control)</li>
 *   <li>Business rule enforcement (validation, authorization, workflow logic)</li>
 *   <li>DAO operation coordination (multi-table operations in single transaction)</li>
 *   <li>Exception handling and error translation</li>
 *   <li>Logging of business operations</li>
 * </ul>
 * 
 * <h2>Transaction Management Pattern</h2>
 * Services MUST manually control transactions using Connection objects.
 * The Service layer acquires connections, sets isolation levels, coordinates
 * DAO operations, and handles commit/rollback.
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>Each service class focuses on one aggregate root (UserService, EventService, etc.)</li>
 *   <li>Services inject DAO dependencies (via constructor for unit testing)</li>
 *   <li>Services do NOT expose Connection objects to ViewModels</li>
 *   <li>Business exceptions are domain-specific (not raw SQLExceptions)</li>
 * </ul>
 * 
 * <h2>Testing Strategy</h2>
 * <ul>
 *   <li>Unit Tests: Mock DAO interfaces with Mockito, test business logic in isolation</li>
 *   <li>Integration Tests: Real database connections to test transaction semantics</li>
 * </ul>
 * 
 * @see com.ticketsync.dao
 * @see com.ticketsync.viewmodel
 */
package com.ticketsync.service;
