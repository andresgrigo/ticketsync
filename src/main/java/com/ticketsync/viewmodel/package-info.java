/**
 * Capa de lógica de presentación con propiedades observables de JavaFX.
 * 
 * <h2>Propósito</h2>
 * Envuelve objetos del Modelo en propiedades JavaFX para el enlace de datos con la interfaz
 * y gestiona el estado de presentación.
 * Los ViewModels manejan la lógica específica de la UI manteniendo los controladores de Vista ligeros.
 * 
 * <h2>Responsabilidades del Patrón MVVM</h2>
 * <ul>
 *   <li>Exponer propiedades JavaFX (StringProperty, ObjectProperty, etc.) para el enlace de UI</li>
 *   <li>Proporcionar colecciones ObservableList para controles TableView/ListView</li>
 *   <li>Manejar la validación de entradas del usuario y mensajes de error</li>
 *   <li>Gestionar el estado de la UI (elementos seleccionados, filtros, valores calculados)</li>
 *   <li>Delegar operaciones de negocio a la capa de Servicio</li>
 * </ul>
 * 
 * <h2>Principios de Diseño</h2>
 * <ul>
 *   <li>SIN acceso directo a base de datos (usar capa de Servicio)</li>
 *   <li>SIN lógica de negocio (delegación a la capa de Servicio)</li>
 *   <li>Todos los datos enlazados a la UI deben ser observables (Properties u ObservableList)</li>
 *   <li>Los ViewModels son testeables unitariamente sin el runtime de JavaFX</li>
 * </ul>
 * 
 * @see com.ticketsync.service
 * @see com.ticketsync.model
 */
package com.ticketsync.viewmodel;
