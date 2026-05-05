/**
 * Interfaces de Objeto de Acceso a Datos (DAO) para operaciones de base de datos.
 * 
 * <h2>Patrón de Arquitectura</h2>
 * El diseño basado en interfaces permite pruebas unitarias de la capa de servicio con mocks de Mockito.
 * Todos los métodos DAO aceptan un parámetro Connection para participar en transacciones.
 * 
 * <h2>Participación en Transacciones</h2>
 * Los DAOs NO gestionan transacciones. La capa de servicio controla los límites de transacción
 * creando conexiones, estableciendo niveles de aislamiento, llamando a métodos DAO con la
 * conexión, y manejando commit o rollback.
 * 
 * <h2>Implementaciones</h2>
 * Implementaciones JDBC:
 * <ul>
 *   <li>UserDAOImpl</li>
 *   <li>EventDAOImpl</li>
 *   <li>ZoneDAOImpl, SeatDAOImpl</li>
 *   <li>SaleDAOImpl</li>
 * </ul>
 * 
 * <h2>Contrato de Conexión</h2>
 * <ul>
 *   <li>El parámetro Connection debe ser no-null y estar abierto</li>
 *   <li>El <strong>llamador es dueño del ciclo de vida de la Connection</strong> — los DAOs nunca la cierran</li>
 *   <li>Para {@link com.ticketsync.dao.SeatDAO#selectForUpdate}, la Connection
 *       debe tener el nivel de aislamiento {@code TRANSACTION_SERIALIZABLE} establecido antes de llamar</li>
 * </ul>
 *
 * <h2>Principios de Diseño</h2>
 * <ul>
 *   <li>Todos los métodos aceptan Connection como primer parámetro</li>
 *   <li>Usar PreparedStatement para prevención de inyección SQL</li>
 *   <li>Devolver objetos del modelo de dominio, nunca ResultSets JDBC directos</li>
 *   <li>Claves primarias devueltas como int (tipo PostgreSQL SERIAL)</li>
 *   <li>Lanzar SQLException para errores de base de datos (la capa de servicio maneja la traducción)</li>
 *   <li>Lanzar IllegalArgumentException para argumentos inválidos (entidades null, IDs inválidos)</li>
 * </ul>
 *
 * <h2>Convenciones de Nomenclatura</h2>
 * <ul>
 *   <li>Nomenclatura de interfaz: EntidadDAO (ej., UserDAO)</li>
 *   <li>Nomenclatura de implementación: EntidadDAOImpl (ej., UserDAOImpl)</li>
 *   <li>Métodos CRUD: findById, findAll, insert, update, delete</li>
 *   <li>Variaciones de consulta: findByUsername, findByEventId, findActive</li>       
 *   <li>Operaciones especiales: selectForUpdate, insertSaleItems, updateStatus</li>
 * 
 * @see com.ticketsync.service
 * @see com.ticketsync.model
 */
package com.ticketsync.dao;
