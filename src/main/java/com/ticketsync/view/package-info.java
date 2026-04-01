/**
 * JavaFX controller classes for FXML-based views.
 * 
 * <h2>Purpose</h2>
 * Controllers bind FXML UI components to ViewModel properties and handle user interaction events.
 * Controllers are kept thin, delegating presentation logic to ViewModels.
 * 
 * <h2>MVVM Pattern Responsibilities</h2>
 * <ul>
 *   <li>Initialize UI components from FXML</li>
 *   <li>Bind UI controls to ViewModel properties (TextField.textProperty, etc.)</li>
 *   <li>Handle user events (@FXML methods for button clicks, menu actions)</li>
 *   <li>Navigate between views (Scene switching, dialog launching)</li>
 *   <li>Apply UI-specific formatting (date formatting, currency display)</li>
 * </ul>
 * 
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>FXML files define UI structure (declarative layout)</li>
 *   <li>Controllers wire FXML components to ViewModel properties</li>
 *   <li>NO business logic in controllers (delegate to ViewModel/Service)</li>
 *   <li>NO direct database access (use ViewModel then Service then DAO chain)</li>
 *   <li>Controllers are NOT unit testable (integration test with TestFX)</li>
 * </ul>
 * 
 * <h2>Naming Convention</h2>
 * Controller class corresponds to an FXML file with matching name.
 * Example: LoginController.java uses login.fxml from resources.
 * 
 * @see com.ticketsync.viewmodel
 */
package com.ticketsync.view;
