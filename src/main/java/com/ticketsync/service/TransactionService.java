package com.ticketsync.service;

import com.ticketsync.dao.SaleDAO;
import com.ticketsync.dao.SaleDAOImpl;
import com.ticketsync.dao.SeatDAO;
import com.ticketsync.dao.SeatDAOImpl;
import com.ticketsync.exception.SeatUnavailableException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Clase de servicio para transacciones atómicas de compra de asientos.
 *
 * <p>Cada llamada a {@link #purchaseSeats} se ejecuta dentro de una sola transacción
 * PostgreSQL SERIALIZABLE: los asientos se bloquean, se valida la disponibilidad, se inserta
 * un registro de Venta y sus SaleItem hijos, y los asientos se marcan como VENDIDOS
 * antes de que la transacción haga commit. Cualquier fallo desencadena un rollback completo.
 *
 * <p>El servicio delega todo el SQL a {@link SeatDAO} y {@link SaleDAO}.
 * No hay cadenas SQL aquí.
 */
public class TransactionService {

    private static final Logger LOGGER = LogManager.getLogger(TransactionService.class);

    private final SeatDAO seatDAO;
    private final SaleDAO saleDAO;
    private final AuditService auditService;
    private final ConnectionFactory connFactory;

    /** Constructor de producción. */
    public TransactionService() {
        this.seatDAO = new SeatDAOImpl();
        this.saleDAO = new SaleDAOImpl();
        this.auditService = new AuditService();
        this.connFactory = DatabaseConfig::getConnection;
    }

    /** Constructor de prueba — sin BD requerida. */
    TransactionService(SeatDAO seatDAO, SaleDAO saleDAO, ConnectionFactory connFactory) {
        this(seatDAO, saleDAO, AuditService.noop(), connFactory);
    }

    /** Constructor de prueba con costura de auditoría inyectable. */
    TransactionService(SeatDAO seatDAO, SaleDAO saleDAO,
                       AuditService auditService, ConnectionFactory connFactory) {
        this.seatDAO = seatDAO;
        this.saleDAO = saleDAO;
        this.auditService = auditService;
        this.connFactory = connFactory;
    }

    /**
     * Ejecuta una transacción atómica de compra de asientos con aislamiento SERIALIZABLE.
     *
     * <p>Flujo de transacción:
     * <ol>
     *   <li>Obtener conexión; establecer SERIALIZABLE + autoCommit=false + timeout de sentencia 10 s</li>
     *   <li>Bloquear asientos con SELECT FOR UPDATE</li>
     *   <li>Validar que todos los asientos están DISPONIBLES</li>
     *   <li>Insertar registro de Venta</li>
     *   <li>Insertar registros de SaleItem (uno por asiento)</li>
     *   <li>Actualizar asientos al estado VENDIDO con referencia de venta</li>
     *   <li>COMMIT</li>
     * </ol>
     *
     * @param eventId evento para el que se venden boletos; debe ser positivo
     * @param seatIds IDs de asientos a comprar; no debe ser null ni estar vacío
     * @param total   precio total de compra para todos los asientos; debe ser positivo
     * @return la {@link Sale} comprometida con su saleId generado por BD establecido
     * @throws SeatUnavailableException si algún asiento no está DISPONIBLE, o ocurre un conflicto de serialización
     * @throws IllegalArgumentException si algún parámetro es inválido
     * @throws IllegalStateException    si ningún usuario tiene sesión iniciada vía {@link SessionContext}
     */
    public Sale purchaseSeats(int eventId, List<Integer> seatIds, BigDecimal total)
            throws SeatUnavailableException {
        return purchaseSeats(eventId, seatIds, total, null);
    }

    /**
     * Compra los asientos especificados para un evento, registrando el ID de cabina del vendedor.
     *
     * <p>Esta sobrecarga acepta un identificador de cabina opcional que se almacena en el
     * registro de venta para propósitos de reporte. La compra es idéntica en comportamiento
     * a {@link #purchaseSeats(int, List, BigDecimal)}.
     *
     * @param eventId  evento para el que se compran asientos; debe ser positivo
     * @param seatIds  lista no vacía de IDs de asientos distintos a comprar; no debe ser null
     * @param total    monto total a cobrar; debe ser positivo
     * @param boothId  identificador de cabina del vendedor opcional; puede ser null
     * @return el registro {@link Sale} persistido para la transacción completada
     * @throws SeatUnavailableException si algún asiento ya no está disponible o ocurre un conflicto de concurrencia
     * @throws IllegalArgumentException si algún parámetro falla la validación
     * @throws IllegalStateException    si ningún usuario tiene sesión iniciada vía {@link SessionContext}
     */
    public Sale purchaseSeats(int eventId, List<Integer> seatIds, BigDecimal total, String boothId)
            throws SeatUnavailableException {
        if (eventId <= 0) throw new IllegalArgumentException("eventId must be positive");
        if (seatIds == null || seatIds.isEmpty()) throw new IllegalArgumentException("seatIds must not be null or empty");
        if (seatIds.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("seatIds must not contain null elements");
        if (new HashSet<>(seatIds).size() != seatIds.size()) throw new IllegalArgumentException("seatIds must not contain duplicates");
        if (total == null) throw new IllegalArgumentException("total must not be null");
        if (total.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("total must be positive");

        int vendorId = SessionContext.getCurrentUser()
                .map(User::getUserId)
                .orElseThrow(() -> new IllegalStateException("No user is logged in — cannot process purchase"));

        LOGGER.info("Processing purchase: event={}, seats={}, total={}, vendor={}",
                eventId, seatIds.size(), total, vendorId);

        Connection conn = null;
        try {
            conn = connFactory.get();
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL statement_timeout = '10000'");
            }

            // Paso 1: Bloquear asientos
            List<Seat> lockedSeats = seatDAO.selectForUpdate(conn, seatIds);

            // Guardia: todos los IDs de asientos solicitados deben haber sido encontrados y bloqueados
            if (lockedSeats.size() != seatIds.size()) {
                List<Integer> lockedIds = lockedSeats.stream().map(Seat::getSeatId).collect(Collectors.toList());
                List<Integer> notFound = seatIds.stream().filter(id -> !lockedIds.contains(id)).collect(Collectors.toList());
                throw new SeatUnavailableException("Seat(s) not found: " + notFound, notFound);
            }

            // Paso 2: Validar disponibilidad (AVAILABLE o RESERVED por este vendor)
            String reservedBy = String.valueOf(vendorId);
            List<Integer> unavailable = lockedSeats.stream()
                    .filter(s -> !isSeatPurchasable(s, reservedBy))
                    .map(Seat::getSeatId)
                    .collect(Collectors.toList());
            if (!unavailable.isEmpty()) {
                throw new SeatUnavailableException(
                        "Seat(s) unavailable: " + unavailable, unavailable);
            }

            // Paso 3: Construir Venta
            Sale sale = new Sale();
            sale.setEventId(eventId);
            sale.setVendorId(vendorId);
            sale.setTotalAmount(total);
            sale.setSaleTimestamp(LocalDateTime.now());
            sale.setBoothId(normalizeBoothId(boothId));

            // Paso 4: Insertar Venta
            int saleId = saleDAO.insert(conn, sale);
            sale.setSaleId(saleId);

            // Paso 5: Construir e insertar SaleItems
            BigDecimal pricePerSeat = total.divide(BigDecimal.valueOf(seatIds.size()), 2, RoundingMode.HALF_UP);
            List<SaleItem> items = new ArrayList<>();
            for (int sid : seatIds) {
                SaleItem item = new SaleItem();
                item.setSeatId(sid);
                item.setPricePaid(pricePerSeat);
                items.add(item);
            }
            saleDAO.insertSaleItems(conn, saleId, items);

            // Paso 6: Marcar asientos como VENDIDOS
            seatDAO.updateStatus(conn, seatIds, SeatStatus.SOLD, saleId);

            conn.commit();
            LOGGER.info("Purchase committed: saleId={}, event={}, seats={}, vendor={}",
                    saleId, eventId, seatIds.size(), vendorId);
            auditService.logPurchaseCompleted(sale, seatIds);
            return sale;

        } catch (SeatUnavailableException e) {
            rollbackQuietly(conn, eventId);
            throw e;
        } catch (RuntimeException e) {
            rollbackQuietly(conn, eventId);
            LOGGER.error("Unexpected error during purchase for event={}: {}", eventId, e.getMessage(), e);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(conn, eventId);
            LOGGER.error("Transaction failed for event={}: {}", eventId, e.getMessage(), e);
            throw new SeatUnavailableException(
                    "Purchase failed: concurrent modification", seatIds, e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void rollbackQuietly(Connection conn, int eventId) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                LOGGER.warn("Rollback failed for event={}: {}", eventId, ex.getMessage());
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                LOGGER.warn("Failed to reset autoCommit: {}", ex.getMessage());
            }
            try {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            } catch (SQLException ex) {
                LOGGER.warn("Failed to reset isolation level: {}", ex.getMessage());
            }
            try {
                conn.close();
            } catch (SQLException ex) {
                LOGGER.warn("Failed to close connection: {}", ex.getMessage());
            }
        }
    }

    private static String normalizeBoothId(String boothId) {
        if (boothId == null || boothId.isBlank()) {
            return null;
        }
        return boothId.strip();
    }

    /**
     * Un asiento es comprable si está DISPONIBLE, o si está RESERVADO por el vendor que realiza la compra.
     */
    private static boolean isSeatPurchasable(Seat seat, String reservedBy) {
        return seat.getStatus() == SeatStatus.AVAILABLE
                || (seat.getStatus() == SeatStatus.RESERVED
                    && reservedBy.equals(seat.getReservedBy()));
    }
}
