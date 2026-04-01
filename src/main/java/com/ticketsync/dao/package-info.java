/**
 * Data Access Object (DAO) interfaces for database operations.
 * 
 * <h2>Architecture Pattern</h2>
 * Interface-based design enables service layer unit testing with Mockito mocks.
 * All DAO methods accept Connection parameter for transaction participation.
 * 
 * <h2>Transaction Participation</h2>
 * DAOs do NOT manage transactions. Service layer controls transaction boundaries
 * by creating connections, setting isolation levels, calling DAO methods with the
 * connection, and handling commit or rollback.
 * 
 * <h2>Implementations</h2>
 * JDBC implementations:
 * <ul>
 *   <li>UserDAOImpl</li>
 *   <li>EventDAOImpl</li>
 *   <li>ZoneDAOImpl, SeatDAOImpl</li>
 *   <li>SaleDAOImpl</li>
 * </ul>
 * 
 * <h2>Connection Contract</h2>
 * <ul>
 *   <li>The Connection parameter must be non-null and open</li>
 *   <li>The <strong>caller owns the Connection lifecycle</strong> — DAOs never close it</li>
 *   <li>For {@link com.ticketsync.dao.SeatDAO#selectForUpdate}, the Connection
 *       must have isolation level {@code TRANSACTION_SERIALIZABLE} set before calling</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>All methods accept Connection as first parameter</li>
 *   <li>Use PreparedStatement for SQL injection prevention</li>
 *   <li>Return domain Model objects, never raw JDBC ResultSets</li>
 *   <li>Primary keys returned as int (PostgreSQL SERIAL type)</li>
 *   <li>Throw SQLException for database errors (Service layer handles translation)</li>
 *   <li>Throw IllegalArgumentException for invalid arguments (null entities, invalid IDs)</li>
 * </ul>
 * 
 * <h2>Naming Conventions</h2>
 * <ul>
 *   <li>Interface naming: EntityDAO (e.g., UserDAO)</li>
 *   <li>Implementation naming: EntityDAOImpl (e.g., UserDAOImpl)</li>
 *   <li>CRUD methods: findById, findAll, insert, update, delete</li>
 *   <li>Query variations: findByUsername, findByEventId, findActive</li>
 *   <li>Special operations: selectForUpdate, insertSaleItems, updateStatus</li>
 * </ul>
 * 
 * @see com.ticketsync.service
 * @see com.ticketsync.model
 */
package com.ticketsync.dao;
