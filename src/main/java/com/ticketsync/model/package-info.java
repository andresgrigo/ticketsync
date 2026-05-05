/**
 * Clases del modelo de dominio que representan entidades de negocio.
 * 
 * <h2>Propósito</h2>
 * POJOs de Java puro que mapean a los esquemas de tablas de la base de datos. Las clases del modelo NO tienen
 * lógica de negocio, NO acceden a la base de datos y NO tienen dependencias de JavaFX.
 * 
 * <h2>Principios de Diseño</h2>
 * <ul>
 *   <li>Cada clase mapea a una tabla de la base de datos (User a users, Event a events, etc.)</li>
 *   <li>Los campos coinciden con los nombres de columna de la base de datos (camelCase en Java, snake_case en SQL)</li>
 *   <li>La inmutabilidad no se aplica (JavaFX requiere beans mutables para el binding con TableView)</li>
 *   <li>Las claves primarias son de tipo int correspondiendo a columnas SERIAL de PostgreSQL</li>
 *   <li>Todas las clases implementan equals(), hashCode() y toString()</li>
 * </ul>
 * 
 * <h2>Ejemplo de Uso</h2>
 * Crear una nueva entidad de usuario, establecer propiedades y persistir vía DAO.
 * 
 * @see com.ticketsync.dao
 * @see com.ticketsync.viewmodel
 */
package com.ticketsync.model;
