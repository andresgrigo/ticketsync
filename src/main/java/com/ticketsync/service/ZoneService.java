package com.ticketsync.service;

import com.ticketsync.dao.ZoneDAO;
import com.ticketsync.dao.ZoneDAOImpl;
import com.ticketsync.model.User;
import com.ticketsync.model.Zone;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Clase de servicio para la lógica de negocio de gestión de zonas.
 *
 * <p>Proporciona operaciones de crear, actualizar, eliminar y consultar en la tabla {@code zones},
 * delegando la persistencia a {@link ZoneDAO}. Todos los métodos adquieren su propia
 * {@link Connection} vía el {@link ConnectionFactory} inyectado y la liberan vía
 * try-with-resources.
 *
 * <p>Todas las operaciones mutantes requieren una sesión ADMIN activa en {@link SessionContext}.
 * Se lanza una {@link SecurityException} si el llamador no tiene el rol {@code ADMIN}.
 *
 * <p>Todas las operaciones mutantes registran una entrada de rastro de auditoría al nivel INFO.
 * Las {@link SQLException} de llamadas DAO se capturan, se registran al nivel ERROR y se vuelven a lanzar.
 */
public class ZoneService {

    private static final Logger LOGGER = LogManager.getLogger(ZoneService.class);

    private static final String SQL_COUNT_SEATS = "SELECT COUNT(*) FROM seats WHERE zone_id = ?";

    private final ZoneDAO zoneDAO;
    private final AuditService auditService;
    private final ConnectionFactory connFactory;

    /**
     * Constructor de producción — crea una instancia activa de {@link ZoneDAOImpl} y
     * usa {@link DatabaseConfig#getConnection()} para la adquisición de conexiones.
     */
    public ZoneService() {
        this(new ZoneDAOImpl(), new AuditService(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección completa en pruebas unitarias (sin BD requerida).
     *
     * @param zoneDAO     el stub DAO; no debe ser {@code null}
     * @param connFactory el proveedor de conexiones stub; no debe ser {@code null}
     */
    ZoneService(ZoneDAO zoneDAO, ConnectionFactory connFactory) {
        this(zoneDAO, AuditService.noop(), connFactory);
    }

    /**
     * Constructor de paquete con costura de auditoría inyectable.
     */
    ZoneService(ZoneDAO zoneDAO, AuditService auditService, ConnectionFactory connFactory) {
        this.zoneDAO = zoneDAO;
        this.auditService = auditService;
        this.connFactory = connFactory;
    }

    // -----------------------------------------------------------------------
    // Ayudantes privados
    // -----------------------------------------------------------------------

    private void requireAdminRole() {
        if (!SessionContext.hasRole("ADMIN")) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
    }

    // -----------------------------------------------------------------------
    // API Pública
    // -----------------------------------------------------------------------

    /**
     * Crea una nueva zona para el evento especificado.
     *
     * @param eventId el evento al que asociar la zona; debe ser positivo
     * @param name    el nombre de la zona; no debe estar en blanco
     * @param price   el precio del boleto; debe ser &gt; 0
     * @return el {@code zone_id} generado
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si el nombre está en blanco o el precio no es positivo
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public int createZone(int eventId, String name, BigDecimal price) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        Zone zone = new Zone();
        zone.setEventId(eventId);
        zone.setName(name.strip());
        zone.setPrice(price);
        try (Connection conn = connFactory.get()) {
            int newId = zoneDAO.insert(conn, zone);
            zone.setZoneId(newId);
            LOGGER.info("Admin '{}' created zone '{}'", adminUsername, name);
            auditService.logZoneCreated(zone);
            return newId;
        } catch (SQLException e) {
            LOGGER.error("Failed to create zone '{}'", name, e);
            throw e;
        }
    }

    /**
     * Actualiza el nombre y precio de una zona existente.
     *
     * @param zone la zona con campos actualizados; {@code zoneId} debe ser positivo,
     *             el nombre no debe estar en blanco, el precio debe ser &gt; 0
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si la validación falla
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public void updateZone(Zone zone) throws SQLException {
        requireAdminRole();
        if (zone.getZoneId() <= 0) {
            throw new IllegalArgumentException("zoneId must be a positive integer");
        }
        if (zone.getName() == null || zone.getName().isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (zone.getPrice() == null || zone.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be greater than zero");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            zoneDAO.update(conn, zone);
            LOGGER.info("Admin '{}' updated zone '{}'", adminUsername, zone.getName());
            auditService.logZoneUpdated(zone);
        } catch (SQLException e) {
            LOGGER.error("Failed to update zone '{}'", zone.getName(), e);
            throw e;
        }
    }

    /**
     * Elimina una zona y (vía ON DELETE CASCADE) todos sus asientos asociados.
     *
     * @param zoneId el ID de la zona a eliminar; debe ser positivo
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si {@code zoneId} no es positivo
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public void deleteZone(int zoneId) throws SQLException {
        requireAdminRole();
        if (zoneId <= 0) {
            throw new IllegalArgumentException("zoneId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            Zone existing = zoneDAO.findById(conn, zoneId)
                    .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + zoneId));
            zoneDAO.delete(conn, zoneId);
            LOGGER.info("Admin '{}' deleted zone id '{}'", adminUsername, zoneId);
            auditService.logZoneDeleted(existing);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete zone id '{}'", zoneId, e);
            throw e;
        }
    }

    /**
     * Devuelve todas las zonas para el evento especificado ordenadas por {@code zone_id ASC}.
     *
     * @param eventId el evento del que recuperar zonas
     * @return lista de zonas; nunca {@code null}, puede estar vacía
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    public List<Zone> getZonesByEvent(int eventId) throws SQLException {
        try (Connection conn = connFactory.get()) {
            return zoneDAO.findByEventId(conn, eventId);
        } catch (SQLException e) {
            LOGGER.error("Failed to load zones for event '{}'", eventId, e);
            throw e;
        }
    }

    /**
     * Devuelve el número de asientos pertenecientes a la zona especificada.
     *
     * <p>Ejecuta {@code SELECT COUNT(*) FROM seats WHERE zone_id = ?} inline para
     * evitar agregar métodos a {@link com.ticketsync.dao.SeatDAO} que romperían
     * los mocks de pruebas existentes.
     *
     * @param zoneId el ID de la zona para contar asientos
     * @return el conteo de asientos; 0 si no existen asientos
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    public int countSeatsForZone(int zoneId) throws SQLException {
        try (Connection conn = connFactory.get();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT_SEATS)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
