/**
 * Clases de controlador JavaFX para vistas basadas en FXML.
 * 
 * <h2>Propósito</h2>
 * Los controladores enlazan los componentes de UI de FXML con las propiedades del ViewModel y gestionan los eventos de interacción del usuario.
 * Los controladores se mantienen delgados, delegando la lógica de presentación a los ViewModels.
 * 
 * <h2>Responsabilidades del patrón MVVM</h2>
 * <ul>
 *   <li>Inicializar componentes de UI desde FXML</li>
 *   <li>Enlazar controles de UI a propiedades del ViewModel (TextField.textProperty, etc.)</li>
 *   <li>Gestionar eventos del usuario (métodos @FXML para clics de botón, acciones de menú)</li>
 *   <li>Navegar entre vistas (cambio de escena, lanzamiento de diálogos)</li>
 *   <li>Aplicar formato específico de UI (formato de fecha, visualización de moneda)</li>
 * </ul>
 * 
 * <h2>Principios de diseño</h2>
 * <ul>
 *   <li>Los archivos FXML definen la estructura de la UI (diseño declarativo)</li>
 *   <li>Los controladores conectan los componentes FXML a las propiedades del ViewModel</li>
 *   <li>SIN lógica de negocio en los controladores (delegar al ViewModel/Servicio)</li>
 *   <li>SIN acceso directo a la base de datos (usar la cadena ViewModel → Servicio → DAO)</li>
 *   <li>Los controladores NO son unitariamente testeables (prueba de integración con TestFX)</li>
 * </ul>
 * 
 * <h2>Convención de nombres</h2>
 * La clase del controlador corresponde a un archivo FXML con el mismo nombre.
 * Ejemplo: LoginController.java usa login.fxml de los recursos.
 * 
 * @see com.ticketsync.viewmodel
 */
package com.ticketsync.view;
