package com.ticketsync.service;

import com.ticketsync.dao.EventDAO;
import com.ticketsync.dao.EventDAOImpl;
import com.ticketsync.model.Event;
import com.ticketsync.model.User;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Proporciona una {@link Connection} para cada operación de servicio.
 *
 * <p>La implementación de producción delega a {@link DatabaseConfig#getConnection()}.
 * Las pruebas suministran un sustituto sin efecto para que las pruebas unitarias se ejecuten
 * sin una base de datos activa.
 */
@FunctionalInterface
interface ConnectionFactory {
    Connection get() throws SQLException;
}

/**
 * Clase de servicio para la lógica de negocio de gestión de eventos.
 *
 * <p>Proporciona operaciones de crear, actualizar, eliminar, activar/desactivar y consultar
 * en la tabla {@code events}, delegando la persistencia a {@link EventDAO}. Todos los
 * métodos adquieren su propia {@link Connection} vía
 * {@link DatabaseConfig#getConnection()} y la liberan vía try-with-resources.
 *
 * <p>Todas las operaciones mutantes requieren una sesión ADMIN activa en
 * {@link SessionContext}. Se lanza una {@link SecurityException} si el llamador
 * no tiene el rol {@code ADMIN}.
 *
 * <p>Todas las operaciones mutantes registran una entrada de rastro de auditoría al nivel INFO.
 * Las {@link SQLException} de llamadas DAO se capturan, se registran al nivel ERROR
 * y se vuelven a lanzar.
 */
public class EventService {

    private static final Logger LOGGER = LogManager.getLogger(EventService.class);

    private final EventDAO eventDAO;
    private final AuditService auditService;
    private final ConnectionFactory connFactory;

    /**
     * Constructor de producción — crea una instancia activa de {@link EventDAOImpl} y
     * usa {@link DatabaseConfig#getConnection()} para la adquisición de conexiones.
     */
    public EventService() {
        this(new EventDAOImpl(), new AuditService(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección en pruebas.
     *
     * <p>Acepta un {@link EventDAO} personalizado y recurre a
     * {@link DatabaseConfig#getConnection()} activo para la adquisición de conexiones.
     * Usa la forma de tres argumentos cuando también se requiere una conexión sin efecto.
     *
     * @param eventDAO la implementación DAO a usar; no debe ser {@code null}
     */
    EventService(EventDAO eventDAO) {
        this(eventDAO, AuditService.noop(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección completa en pruebas unitarias (sin BD requerida).
     *
     * <p>Permite a las pruebas unitarias puras inyectar tanto un {@link EventDAO} stub como una
     * {@link ConnectionFactory} sin efecto para que las pruebas nunca toquen la base de datos.
     *
     * @param eventDAO    el stub DAO; no debe ser {@code null}
     * @param connFactory el proveedor de conexiones stub; no debe ser {@code null}
     */
    EventService(EventDAO eventDAO, ConnectionFactory connFactory) {
        this(eventDAO, AuditService.noop(), connFactory);
    }

    /**
     * Constructor de paquete con costura de auditoría inyectable.
     */
    EventService(EventDAO eventDAO, AuditService auditService, ConnectionFactory connFactory) {
        this.eventDAO = eventDAO;
        this.auditService = auditService;
        this.connFactory = connFactory;
    }

    // -----------------------------------------------------------------------
    // Ayudantes privados
    // -----------------------------------------------------------------------

    /**
     * Verifica que la sesión actual pertenece a un usuario con el rol ADMIN.
     *
     * @throws SecurityException si ningún usuario está autenticado o el usuario autenticado
     *                           no tiene el rol ADMIN
     */
    private void requireAdminRole() {
        if (!SessionContext.hasRole("ADMIN")) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
    }

    /**
     * Verifica que un usuario esté actualmente autenticado (cualquier rol).
     *
     * @throws SecurityException si ningún usuario está autenticado
     */
    private void requireAuthenticated() {
        if (SessionContext.getCurrentUser().isEmpty()) {
            throw new SecurityException("Access denied: authentication required");
        }
    }

    /**
     * Valida que los campos requeridos del evento estén presentes y sean lógicamente correctos.
     *
     * @param event el evento a validar; no debe ser {@code null}
     * @throws IllegalArgumentException si algún campo requerido es null, está en blanco o
     *                                  la fecha del evento no está en el futuro
     */
    private void validateEventFields(Event event) {
        if (event.getName() == null || event.getName().isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        if (event.getEventDate() == null || !event.getEventDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("eventDate must be a future date/time");
        }
        if (event.getVenue() == null || event.getVenue().isBlank()) {
            throw new IllegalArgumentException("venue must not be null or blank");
        }
    }

    // -----------------------------------------------------------------------
    // API Pública
    // -----------------------------------------------------------------------

    /**
     * Crea un nuevo evento en la base de datos.
     *
     * <p>Requiere una sesión ADMIN activa. El campo {@code createdBy} del
     * evento suministrado se establece al ID del usuario actual antes del insert.
     *
     * @param event el evento a crear; campos requeridos: name, eventDate (futuro), venue
     * @return el {@code event_id} generado
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si algún campo requerido es inválido
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public int createEvent(Event event) throws SQLException {
        requireAdminRole();
        validateEventFields(event);
        event.setCreatedBy(SessionContext.getCurrentUser().orElseThrow(() -> new SecurityException("No active session")).getUserId());
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            int newId = eventDAO.insert(conn, event);
            event.setEventId(newId);
            LOGGER.info("Admin '{}' created event '{}'", adminUsername, event.getName());
            auditService.logEventCreated(event);
            return newId;
        } catch (SQLException e) {
            LOGGER.error("Failed to create event '{}'", event.getName(), e);
            throw e;
        }
    }

    /**
     * Actualiza un evento existente en la base de datos.
     *
     * <p>Requiere una sesión ADMIN activa y un ID de evento válido (positivo).
     * Lanza {@link IllegalArgumentException} si el evento no existe.
     *
     * @param event el evento a actualizar; {@code eventId} debe ser positivo
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si {@code eventId} no es positivo, o
     *                                  el evento no existe en la base de datos
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public void updateEvent(Event event) throws SQLException {
        requireAdminRole();
        if (event.getEventId() <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        validateEventFields(event);
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            int eventId = event.getEventId();
            eventDAO.findById(conn, eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            eventDAO.update(conn, event);
            LOGGER.info("Admin '{}' updated event '{}'", adminUsername, event.getName());
            auditService.logEventUpdated(event);
        } catch (SQLException e) {
            LOGGER.error("Failed to update event '{}'", event.getName(), e);
            throw e;
        }
    }

    /**
     * Elimina un evento de la base de datos.
     *
     * <p>Requiere una sesión ADMIN activa y un ID de evento válido (positivo).
     *
     * @param eventId el ID del evento a eliminar; debe ser positivo
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si {@code eventId} no es positivo
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public void deleteEvent(int eventId) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            Event existing = eventDAO.findById(conn, eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            eventDAO.delete(conn, eventId);
            LOGGER.info("Admin '{}' deleted event id '{}'", adminUsername, eventId);
            auditService.logEventDeleted(existing);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete event id '{}'", eventId, e);
            throw e;
        }
    }

    /**
     * Activa un evento para que aparezca en el selector de eventos del POS para vendedores.
     *
     * <p>Carga el evento completo desde la base de datos, establece {@code isActive = true},
     * y persiste el cambio vía {@link EventDAO#update(Connection, Event)}.
     *
     * @param eventId el ID del evento a activar; debe ser positivo
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si el evento no existe
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public void activateEvent(int eventId) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            Event event = eventDAO.findById(conn, eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            event.setActive(true);
            eventDAO.update(conn, event);
            LOGGER.info("Admin '{}' activated event '{}'", adminUsername, event.getName());
            auditService.logEventActivated(event);
        } catch (SQLException e) {
            LOGGER.error("Failed to activate event id '{}'", eventId, e);
            throw e;
        }
    }

    /**
     * Desactiva un evento para que no aparezca en el selector de eventos del POS para vendedores.
     *
     * <p>Carga el evento completo desde la base de datos, establece {@code isActive = false},
     * y persiste el cambio vía {@link EventDAO#update(Connection, Event)}.
     *
     * @param eventId el ID del evento a desactivar; debe ser positivo
     * @throws SecurityException        si el usuario actual no tiene el rol ADMIN
     * @throws IllegalArgumentException si el evento no existe
     * @throws SQLException             si ocurre un error de acceso a la base de datos
     */
    public void deactivateEvent(int eventId) throws SQLException {
        requireAdminRole();
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be a positive integer");
        }
        String adminUsername = SessionContext.getCurrentUser().map(User::getUsername).orElse("unknown");
        try (Connection conn = connFactory.get()) {
            Event event = eventDAO.findById(conn, eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
            event.setActive(false);
            eventDAO.update(conn, event);
            LOGGER.info("Admin '{}' deactivated event '{}'", adminUsername, event.getName());
            auditService.logEventDeactivated(event);
        } catch (SQLException e) {
            LOGGER.error("Failed to deactivate event id '{}'", eventId, e);
            throw e;
        }
    }

    /**
    /**
     * Devuelve todos los eventos activos.
     *
     * <p>Requiere una sesión autenticada (cualquier rol — los vendedores necesitan esto para el selector de eventos POS).
     *
     * @return lista de eventos activos; nunca {@code null}, puede estar vacía
     * @throws SecurityException si ningún usuario está autenticado
     * @throws SQLException si ocurre un error de acceso a la base de datos
     */
    public List<Event> getActiveEvents() throws SQLException {
        requireAuthenticated();
        try (Connection conn = connFactory.get()) {
            List<Event> result = eventDAO.findActive(conn);
            return result != null ? result : Collections.emptyList();
        } catch (SQLException e) {
            LOGGER.error("Failed to retrieve active events", e);
            throw e;
        }
    }

    /**
     * Devuelve todos los eventos ordenados por {@code event_date DESC}.
     *
     * <p>Requiere una sesión ADMIN activa.
     *
     * @return lista de todos los eventos; nunca {@code null}, puede estar vacía
     * @throws SecurityException si el usuario actual no tiene el rol ADMIN
     * @throws SQLException      si ocurre un error de acceso a la base de datos
     */
    public List<Event> findAllEvents() throws SQLException {
        requireAdminRole();
        try (Connection conn = connFactory.get()) {
            return eventDAO.findAll(conn);
        } catch (SQLException e) {
            LOGGER.error("Failed to retrieve all events", e);
            throw e;
        }
    }
}
