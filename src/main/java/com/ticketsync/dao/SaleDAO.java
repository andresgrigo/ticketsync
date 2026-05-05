package com.ticketsync.dao;

import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Sale database operations.
 * 
 * <h2>Transaction Participation</h2>
 * All methods accept a Connection parameter to participate in service-layer transactions.
 * Implementations use PreparedStatement for SQL injection prevention.
 * 
 * <h2>Database Mapping</h2>
 * <ul>
 *   <li>Database Table: sales</li>
 *   <li>Primary Key: sale_id (SERIAL)</li>
 *   <li>Foreign Keys: event_id → events.event_id, vendor_id → users.user_id</li>
 * </ul>
 * 
 * <h2>Transaction Semantics</h2>
 * Sale insertion is part of a multi-step atomic transaction:
 * <ol>
 *   <li>Lock seats with SeatDAO.selectForUpdate()</li>
 *   <li>Validate seat availability</li>
 *   <li>Insert Sale record (this DAO's insert method)</li>
 *   <li>Insert SaleItem records (this DAO's insertSaleItems method)</li>
 *   <li>Update seat status with SeatDAO.updateStatus()</li>
 *   <li>Commit transaction</li>
 * </ol>
 * 
 * @see com.ticketsync.model.Sale
 * @see SaleItem
 */
public interface SaleDAO {
    
    /**
     * Encuentra una venta por clave primaria.
     *
     * @param conn Conexión de base de datos activa
     * @param saleId Clave primaria de la venta a recuperar
     * @return Optional que contiene Sale si se encontró, vacío en caso contrario
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si saleId es cero o negativo
     */
    Optional<Sale> findById(Connection conn, int saleId) throws SQLException;

    /**
     * Recupera los ítems de venta confirmados para una venta específica.
     *
     * @param conn Conexión de base de datos activa
     * @param saleId ID de la venta cuyos ítems deben ser devueltos
     * @return Lista de ítems de venta confirmados en orden de inserción, vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si saleId es cero o negativo
     */
    List<SaleItem> findSaleItemsBySaleId(Connection conn, int saleId) throws SQLException;
    
    /**
     * Recupera todas las ventas de un evento específico.
     * Utilizado en reportes de ventas del administrador.
     *
     * @param conn Conexión de base de datos activa
     * @param eventId ID del evento para recuperar ventas
     * @return Lista de ventas del evento, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<Sale> findByEventId(Connection conn, int eventId) throws SQLException;
    
    /**
     * Recupera todas las ventas de un vendedor específico en una fecha específica.
     * Utilizado en el reporte de ventas diario del vendedor.
     *
     * @param conn Conexión de base de datos activa
     * @param vendorId ID de usuario del vendedor
     * @param date Fecha para recuperar ventas. Las implementaciones deben comparar contra la
     *        columna {@code sale_timestamp} convertida a la fecha local del servidor de base de datos.
     *        Los llamadores deben asegurar que la fecha refleje la zona horaria del servidor (generalmente UTC)
     *        para evitar errores de desfase en los límites de medianoche.
     * @return Lista de ventas del vendedor en la fecha, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<Sale> findByVendor(Connection conn, int vendorId, LocalDate date) throws SQLException;
    
    /**
     * Inserta un nuevo registro de venta en la base de datos.
     *
     * <p>Este método se llama dentro de una transacción DESPUÉS de validar la disponibilidad
     * de asientos y ANTES de actualizar el estado del asiento. Ver la documentación de clase
     * {@link SaleDAO} para el flujo completo de transacción.
     *
     * @param conn Conexión de base de datos activa
     * @param sale Objeto Sale a insertar (el campo saleId se ignora; la base de datos genera la clave)
     * @return sale_id generado por la base de datos
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si sale es null
     */
    int insert(Connection conn, Sale sale) throws SQLException;
    
    /**
     * Inserta registros de ítems de venta vinculando asientos a una venta.
     *
     * <p>Este método se llama dentro de la misma transacción que {@link #insert},
     * inmediatamente después de que se crea el registro Sale.
     *
     * @param conn Conexión de base de datos activa
     * @param saleId ID de venta para asociar ítems
     * @param items Lista de objetos SaleItem a insertar (el campo saleItemId se ignora;
     *        la base de datos genera cada clave). Debe ser no-null y no-vacía.
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si items es null o vacía
     */
    void insertSaleItems(Connection conn, int saleId, List<SaleItem> items) throws SQLException;
}
