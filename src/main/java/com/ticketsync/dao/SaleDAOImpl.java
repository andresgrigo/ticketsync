package com.ticketsync.dao;

import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementación JDBC de {@link SaleDAO} para las tablas {@code sales} y {@code sale_items}.
 *
 * <p>Todo el SQL se ejecuta vía {@link PreparedStatement} usando marcadores {@code ?}.
 * Nunca se construye SQL por concatenación de cadenas, previniendo inyección SQL (OWASP A03).
 *
 * <p>Los llamadores son responsables de gestionar el ciclo de vida de {@link Connection} (abrir,
 * commit/rollback, cerrar). Esta clase nunca cierra la conexión suministrada.
 *
 * <p>La coordinación de transacciones (autoCommit=false, aislamiento SERIALIZABLE, commit/rollback)
 * es responsabilidad de la capa de servicio (TransactionService).
 *
 * @see SaleDAO
 * @see com.ticketsync.model.Sale
 * @see com.ticketsync.model.SaleItem
 */
public class SaleDAOImpl implements SaleDAO {

    /** Crea un nuevo {@code SaleDAOImpl} usando la fábrica de conexiones predeterminada. */
    public SaleDAOImpl() {
    }

    // -------------------------------------------------------------------------
    // Constantes SQL
    // -------------------------------------------------------------------------

    private static final String SQL_INSERT_SALE =
            "INSERT INTO sales (event_id, vendor_id, total_amount, sale_timestamp, booth_id) "
            + "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_INSERT_SALE_ITEM =
            "INSERT INTO sale_items (sale_id, seat_id, price_paid) VALUES (?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
            "SELECT sale_id, event_id, vendor_id, total_amount, sale_timestamp, booth_id "
            + "FROM sales WHERE sale_id = ?";

    private static final String SQL_FIND_BY_EVENT_ID =
            "SELECT sale_id, event_id, vendor_id, total_amount, sale_timestamp, booth_id "
            + "FROM sales WHERE event_id = ? ORDER BY sale_timestamp DESC";

    private static final String SQL_FIND_SALE_ITEMS_BY_SALE_ID =
            "SELECT sale_item_id, sale_id, seat_id, price_paid "
            + "FROM sale_items WHERE sale_id = ? ORDER BY sale_item_id ASC";

    private static final String SQL_FIND_BY_VENDOR =
            "SELECT sale_id, event_id, vendor_id, total_amount, sale_timestamp, booth_id "
            + "FROM sales WHERE vendor_id = ? AND sale_timestamp::date = ? ORDER BY sale_timestamp DESC";

    // -------------------------------------------------------------------------
    // Logger
    // -------------------------------------------------------------------------

    private static final Logger LOGGER = LogManager.getLogger(SaleDAOImpl.class);

    // -------------------------------------------------------------------------
    // Métodos públicos de interfaz
    // -------------------------------------------------------------------------

    /**
     * Encuentra una venta por clave primaria.
     *
     * @param conn   Conexión de base de datos activa
     * @param saleId Clave primaria de la venta a recuperar
     * @return {@code Optional} que contiene {@link Sale} si se encontró, vacío en caso contrario
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     * @throws IllegalArgumentException si {@code saleId} es cero o negativo
     */
    @Override
    public Optional<Sale> findById(Connection conn, int saleId) throws SQLException {
        if (saleId <= 0) {
            throw new IllegalArgumentException("saleId must be positive, got: " + saleId);
        }
        LOGGER.debug("Finding sale by id={}", saleId);
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
    * Recupera todas las ventas de un evento específico, ordenadas por {@code sale_timestamp DESC}.
     *
     * @param conn    Conexión de base de datos activa
     * @param eventId ID del evento para recuperar ventas
     * @return Lista de ventas del evento; lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    @Override
    public List<Sale> findByEventId(Connection conn, int eventId) throws SQLException {
        LOGGER.debug("Finding sales by eventId={}", eventId);
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_EVENT_ID)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapRow(rs));
                }
            }
        }
        return sales;
    }

    @Override
    public List<SaleItem> findSaleItemsBySaleId(Connection conn, int saleId) throws SQLException {
        if (saleId <= 0) {
            throw new IllegalArgumentException("saleId must be positive, got: " + saleId);
        }
        LOGGER.debug("Finding sale items by saleId={}", saleId);
        List<SaleItem> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_SALE_ITEMS_BY_SALE_ID)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapSaleItem(rs));
                }
            }
        }
        return items;
    }

    /**
    * Recupera todas las ventas de un vendedor específico en una fecha específica.
     *
     * <p>Usa el cast {@code ::date} de PostgreSQL para comparar la columna {@code sale_timestamp}
     * contra la fecha suministrada. Nota: el cast usa la fecha local del servidor de base de datos
     * — los llamadores deben asegurar que la fecha suministrada coincida con la zona horaria del servidor (generalmente UTC)
     * para evitar errores de desfase en los límites de medianoche.
     *
     * @param conn     Conexión de base de datos activa
     * @param vendorId ID de usuario del vendedor
     * @param date     Fecha de calendario para coincidir con {@code sale_timestamp::date}
     * @return Lista de ventas del vendedor en la fecha dada; lista vacía si no existen
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    @Override
    public List<Sale> findByVendor(Connection conn, int vendorId, LocalDate date) throws SQLException {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        LOGGER.debug("Finding sales by vendorId={}, date={}", vendorId, date);
        List<Sale> sales = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_VENDOR)) {
            ps.setInt(1, vendorId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapRow(rs));
                }
            }
        }
        return sales;
    }

    /**
    * Inserta un nuevo registro de venta y devuelve el {@code sale_id} generado por la base de datos.
     *
     * <p>Este método se llama dentro de una transacción DESPUÉS de validar la disponibilidad
     * de asientos y ANTES de actualizar el estado del asiento. Ver la documentación de clase
     * {@link SaleDAO} para el flujo completo de transacción.
     *
     * @param conn Conexión de base de datos activa
     * @param sale Venta a insertar; el campo {@code saleId} se ignora — la base de datos lo genera
     * @return {@code sale_id} generado
     * @throws SQLException             si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si {@code sale} es null, o si {@code sale.saleTimestamp} es null
     */
    @Override
    public int insert(Connection conn, Sale sale) throws SQLException {
        if (sale == null) {
            throw new IllegalArgumentException("sale must not be null");
        }
        if (sale.getSaleTimestamp() == null) {
            throw new IllegalArgumentException("sale.saleTimestamp must not be null");
        }
        LOGGER.debug("Inserting sale for event={}, vendor={}", sale.getEventId(), sale.getVendorId());
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_SALE, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sale.getEventId());
            ps.setInt(2, sale.getVendorId());
            ps.setBigDecimal(3, sale.getTotalAmount());
            ps.setTimestamp(4, Timestamp.valueOf(sale.getSaleTimestamp()));
            ps.setString(5, sale.getBoothId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Insert succeeded but no generated key was returned");
            }
        }
    }

    /**
    * Inserta registros de ítems de venta vinculando asientos a una venta.
     *
     * <p>Este método se llama dentro de la misma transacción que {@link #insert},
     * inmediatamente después de crear el registro Sale.
     *
     * @param conn   Conexión de base de datos activa
     * @param saleId ID de venta para asociar ítems; debe ser positivo
     * @param items  Lista de objetos {@link SaleItem} a insertar; debe ser no-null y no-vacía
     * @throws SQLException             si ocurre un error de acceso a la base de datos o violación de restricción
     * @throws IllegalArgumentException si {@code saleId} es cero o negativo, o si {@code items} es null o vacía
     */
    @Override
    public void insertSaleItems(Connection conn, int saleId, List<SaleItem> items) throws SQLException {
        if (saleId <= 0) {
            throw new IllegalArgumentException("saleId must be positive, got: " + saleId);
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }
        LOGGER.debug("Inserting {} sale items for saleId={}", items.size(), saleId);
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_SALE_ITEM)) {
            for (SaleItem item : items) {
                ps.setInt(1, saleId);
                ps.setInt(2, item.getSeatId());
                ps.setBigDecimal(3, item.getPricePaid());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // -------------------------------------------------------------------------
    // Ayudantes privados
    // -------------------------------------------------------------------------

    /**
     * Mapea la fila actual de un {@link ResultSet} a un objeto {@link Sale}.
     *
     * <p>La columna nullable {@code booth_id} es manejada por {@link ResultSet#getString},
     * que devuelve {@code null} para valores SQL NULL.
     *
     * @param rs ResultSet posicionado en la fila actual
     * @return instancia de {@link Sale} poblada
     * @throws SQLException si una columna no puede ser leída
     */
    private Sale mapRow(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setSaleId(rs.getInt("sale_id"));
        sale.setEventId(rs.getInt("event_id"));
        sale.setVendorId(rs.getInt("vendor_id"));
        sale.setTotalAmount(rs.getBigDecimal("total_amount"));
        Timestamp ts = rs.getTimestamp("sale_timestamp");
        sale.setSaleTimestamp(ts != null ? ts.toLocalDateTime() : null);
        sale.setBoothId(rs.getString("booth_id"));
        return sale;
    }

    private SaleItem mapSaleItem(ResultSet rs) throws SQLException {
        SaleItem item = new SaleItem();
        item.setSaleItemId(rs.getInt("sale_item_id"));
        item.setSaleId(rs.getInt("sale_id"));
        item.setSeatId(rs.getInt("seat_id"));
        item.setPricePaid(rs.getBigDecimal("price_paid"));
        return item;
    }
}
