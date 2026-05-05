/**
 * Capa de lógica de negocio y gestión de transacciones.
 *
 * <h2>Propósito</h2>
 * La capa de servicio orquesta operaciones DAO dentro de transacciones, aplica reglas de
 * negocio y proporciona una API limpia para que los ViewModels ejecuten operaciones del dominio.
 *
 * <h2>Responsabilidades Arquitectónicas</h2>
 * <ul>
 *   <li>Gestión de límites de transacción (control del ciclo de vida de Connection)</li>
 *   <li>Aplicación de reglas de negocio (validación, autorización, lógica de flujo de trabajo)</li>
 *   <li>Coordinación de operaciones DAO (operaciones multi-tabla en una sola transacción)</li>
 *   <li>Manejo de excepciones y traducción de errores</li>
 *   <li>Registro de operaciones de negocio</li>
 * </ul>
 *
 * <h2>Patrón de Gestión de Transacciones</h2>
 * Los servicios DEBEN controlar manualmente las transacciones usando objetos Connection.
 * La capa de servicio adquiere conexiones, establece niveles de aislamiento, coordina
 * operaciones DAO y gestiona commit/rollback.
 *
 * <h2>Principios de Diseño</h2>
 * <ul>
 *   <li>Cada clase de servicio se centra en una raíz agregada (UserService, EventService, etc.)</li>
 *   <li>Los servicios inyectan dependencias DAO (vía constructor para pruebas unitarias)</li>
 *   <li>Los servicios NO exponen objetos Connection a los ViewModels</li>
 *   <li>Las excepciones de negocio son específicas del dominio (no SQLExceptions crudas)</li>
 * </ul>
 *
 * <h2>Estrategia de Pruebas</h2>
 * <ul>
 *   <li>Pruebas Unitarias: Simular interfaces DAO con Mockito, probar lógica de negocio en aislamiento</li>
 *   <li>Pruebas de Integración: Conexiones reales a BD para probar semántica de transacciones</li>
 * </ul>
 *
 * @see com.ticketsync.dao
 * @see com.ticketsync.viewmodel
 */
package com.ticketsync.service;
