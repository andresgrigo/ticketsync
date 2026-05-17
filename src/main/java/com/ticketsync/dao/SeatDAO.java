package com.ticketsync.dao;

import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Seat database operations.
 * 
 * <h2>Transaction Participation</h2>
 * All methods accept a Connection parameter to participate in service-layer transactions.
 * Implementations use PreparedStatement for SQL injection prevention.
 * 
 * <h2>Database Mapping</h2>
 * <ul>
 *   <li>Database Table: seats</li>
 *   <li>Primary Key: seat_id (SERIAL)</li>
 *   <li>Foreign Keys: zone_id → zones.zone_id, sale_id → sales.sale_id</li>
 * </ul>
 * 
 * <h2>Concurrency Control</h2>
 * The {@link #selectForUpdate} method is CRITICAL for preventing oversells.
 * It uses PostgreSQL row-level locking with SELECT FOR UPDATE to guarantee
 * exclusive access to seats during purchase transactions.
 * 
 * @see com.ticketsync.model.Seat
 * @see SeatStatus
 */
public interface SeatDAO {
    
    /**
     * Encuentra un asiento por clave primaria.
     *
     * @param conn Conexión de base de datos activa
     * @param seatId Clave primaria del asiento a recuperar
     * @return Optional que contiene Seat si se encontró, vacío en caso contrario
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si seatId es cero o negativo
     */
    Optional<Seat> findById(Connection conn, int seatId) throws SQLException;
    
    /**
     * Recupera todos los asientos de una zona específica.
     * Utilizado en el editor de plano de asientos del administrador.
     *
     * @param conn Conexión de base de datos activa
     * @param zoneId ID de zona para recuperar asientos
     * @return Lista de asientos en la zona, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<Seat> findByZoneId(Connection conn, int zoneId) throws SQLException;
    
    /**
     * Recupera todos los asientos de un evento específico (en todas las zonas).
     * Utilizado en la visualización del mapa de asientos del POS del vendedor.
     *
     * @param conn Conexión de base de datos activa
     * @param eventId ID del evento para recuperar asientos
     * @return Lista de asientos del evento, lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    List<Seat> findByEventId(Connection conn, int eventId) throws SQLException;
    
    /**
     * Locks seats for update in SERIALIZABLE transaction, preventing concurrent modifications.
     * Uses PostgreSQL row-level locking (SELECT ... FOR UPDATE) to guarantee exclusive access.
     * 
     * <p><strong>CRITICAL METHOD:</strong> This is the most important method for preventing oversells.
     * It MUST be called first in any seat purchase transaction to prevent race conditions.
     * 
     * <h4>Implementation Pattern</h4>
     * <pre>{@code
     * String sql = "SELECT * FROM seats WHERE seat_id = ANY(?) FOR UPDATE";
     * PreparedStatement stmt = conn.prepareStatement(sql);
     * stmt.setArray(1, conn.createArrayOf("INTEGER", seatIds.toArray()));
     * ResultSet rs = stmt.executeQuery();
     * return mapResultSet(rs);
     * }</pre>
     * 
     * <h4>Locking Behavior</h4>
     * <ul>
     *   <li>Other transactions attempting to SELECT FOR UPDATE same seats will WAIT</li>
     *   <li>SERIALIZABLE isolation + FOR UPDATE = Zero oversells guaranteed</li>
     *   <li>Locks held until Connection.commit() or Connection.rollback()</li>
     * </ul>
     * 
     * @param conn Active database connection with SERIALIZABLE isolation level set
     * @param seatIds List of seat IDs to lock for purchase
     * @return List of Seat objects with current status (locked exclusively to this connection)
     * @throws SQLException if seats don't exist or connection fails
     * @throws IllegalArgumentException if seatIds is null, empty, or contains null elements
     * @throws IllegalStateException if connection isolation level is not TRANSACTION_SERIALIZABLE
     */
    List<Seat> selectForUpdate(Connection conn, List<Integer> seatIds) throws SQLException;
    
    /**
     * Actualiza atómicamente el estado del asiento y la referencia de venta.
     * Utilizado en la transacción de compra para marcar asientos como SOLD.
     *
     * <p>Este método se llama DESPUÉS de {@link #selectForUpdate} en la transacción.
     *
     * @param conn Conexión de base de datos activa
     * @param seatIds Lista de IDs de asientos a actualizar
     * @param status Nuevo estado del asiento (generalmente SOLD)
     * @param saleId ID de venta para asociar con asientos (null para estado AVAILABLE/DISABLED/RESERVED)
     * @throws SQLException si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si seatIds es null o vacía, status es null, o status es
     *         SOLD con saleId null
     */
    void updateStatus(Connection conn, List<Integer> seatIds, SeatStatus status, Integer saleId)
            throws SQLException;
    
    /**
     * Inserta un nuevo asiento en la base de datos.
     * Utilizado en el editor de plano de asientos del administrador.
     *
     * @param conn Conexión de base de datos activa
     * @param seat Objeto Seat a insertar (el campo seatId se ignora; la base de datos genera la clave)
     * @return seat_id generado por la base de datos
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si seat es null
     */
    int insert(Connection conn, Seat seat) throws SQLException;
    
    /**
     * Elimina un asiento de la base de datos.
     * Utilizado en el editor de plano de asientos del administrador cuando se eliminan asientos.
     *
     * <p><strong>Nota:</strong> La eliminación puede fallar debido a restricciones de clave foránea
     * si el asiento ha sido vendido (clave foránea sale_id).
     *
     * @param conn Conexión de base de datos activa
     * @param seatId Clave primaria del asiento a eliminar
     * @throws SQLException si ocurre un error de acceso a la base de datos o violación de restricción
     */
    void delete(Connection conn, int seatId) throws SQLException;

    /**
     * Reserva atómicamente los asientos especificados para el vendor dado.
     *
     * <p>Solo los asientos con estado {@code AVAILABLE}, o con estado {@code RESERVED} cuya
     * {@code reserved_until} haya expirado, son elegibles. Los asientos ya reservados por otro
     * vendor son omitidos silenciosamente. Los asientos reservados se establecen al estado
     * {@code RESERVED} con {@code reserved_by = reservedBy} y
     * {@code reserved_until = NOW() + ttlSeconds segundos}.
     *
     * @param conn        conexión activa; autoCommit puede ser true o false
     * @param seatIds     IDs de asientos a reservar; no debe ser {@code null} ni vacía
     * @param reservedBy  identificador del vendor (p.ej., userId como texto); no debe ser {@code null}
     * @param ttlSeconds  segundos hasta que la reserva expire; debe ser positivo
     * @return lista de IDs de asientos que fueron efectivamente reservados
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si {@code seatIds} es null/vacía, {@code reservedBy} es null,
     *                                  o {@code ttlSeconds} no es positivo
     */
    List<Integer> reserveSeats(Connection conn, List<Integer> seatIds, String reservedBy, int ttlSeconds)
            throws SQLException;

    /**
     * Libera las reservas activas que coincidan con {@code reservedBy} para los asientos dados.
     *
     * <p>Solo los asientos con estado {@code RESERVED} cuyo {@code reserved_by} sea igual a
     * {@code reservedBy} son afectados. Devuelve el estado a {@code AVAILABLE} y borra las
     * columnas de reserva. Asientos no encontrados o propiedad de otro vendor son ignorados.
     *
     * @param conn       conexión activa
     * @param seatIds    IDs de asientos a liberar; no debe ser {@code null} ni vacía
     * @param reservedBy identificador del vendor que posee la reserva; no debe ser {@code null}
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si {@code seatIds} es null/vacía o {@code reservedBy} es null
     */
    void releaseReservation(Connection conn, List<Integer> seatIds, String reservedBy) throws SQLException;
}
