/**
 * Presentation logic layer with JavaFX observable properties.
 * 
 * <h2>Purpose</h2>
 * Wraps Model objects in JavaFX properties for UI data binding and manages presentation state.
 * ViewModels handle UI-specific logic while keeping View controllers thin.
 * 
 * <h2>MVVM Pattern Responsibilities</h2>
 * <ul>
 *   <li>Expose JavaFX properties (StringProperty, ObjectProperty, etc.) for UI binding</li>
 *   <li>Provide ObservableList collections for TableView/ListView controls</li>
 *   <li>Handle user input validation and error messaging</li>
 *   <li>Manage UI state (selected items, filters, computed values)</li>
 *   <li>Delegate business operations to Service layer</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>NO direct database access (use Service layer)</li>
 *   <li>NO business logic (delegation to Service layer)</li>
 *   <li>All UI-bound data must be observable (Properties or ObservableList)</li>
 *   <li>ViewModels are unit testable without JavaFX runtime</li>
 * </ul>
 * 
 * @see com.ticketsync.service
 * @see com.ticketsync.model
 */
package com.ticketsync.viewmodel;
