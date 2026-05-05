package com.ticketsync.service;

import com.ticketsync.dao.AuditDAO;
import com.ticketsync.dao.AuditDAOImpl;
import com.ticketsync.model.AuditLog;
import com.ticketsync.model.Event;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.User;
import com.ticketsync.model.Zone;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio para escrituras de auditoría de negocio y consultas de auditoría de solo lectura.
 */
public class AuditService {

    /**
     * Nombres de acción de auditoría estables usados en eventos de negocio de la capa de servicio.
     */
    public enum Action {
        /** Asientos comprados exitosamente en una sola transacción atómica. */
        PURCHASE_SEATS,
        /** Usuario autenticado exitosamente. */
        LOGIN_SUCCESS,
        /** Intento de inicio de sesión fallido (credenciales incorrectas o usuario desconocido). */
        LOGIN_FAILURE,
        /** Nueva cuenta de usuario creada por un administrador. */
        USER_CREATED,
        /** Rol o credenciales de usuario existente actualizados por un administrador. */
        USER_ROLE_UPDATED,
        /** Cuenta de usuario eliminada por un administrador. */
        USER_DELETED,
        /** Nuevo evento creado por un administrador. */
        EVENT_CREATED,
        /** Detalles de evento existente editados por un administrador. */
        EVENT_UPDATED,
        /** Evento eliminado por un administrador. */
        EVENT_DELETED,
        /** Evento habilitado para ventas de boletos por un administrador. */
        EVENT_ACTIVATED,
        /** Evento deshabilitado de ventas de boletos por un administrador. */
        EVENT_DEACTIVATED,
        /** Nueva zona de precios creada para un evento. */
        ZONE_CREATED,
        /** Detalles de zona existente editados. */
        ZONE_UPDATED,
        /** Zona eliminada de un evento. */
        ZONE_DELETED,
        /** Disposición de asientos generada (filas y asientos asignados a una zona). */
        SEATS_GENERATED,
        /** Disposición de asientos eliminada para una zona. */
        SEATS_DELETED,
        /** Disponibilidad de asiento individual alternada por un administrador. */
        SEAT_STATUS_UPDATED
    }

    private static final Logger LOGGER = LogManager.getLogger(AuditService.class);
    private static final Set<String> SUPPORTED_ACTIONS = Arrays.stream(Action.values())
            .map(Action::name)
            .collect(Collectors.toUnmodifiableSet());

    private final AuditDAO auditDAO;
    private final ConnectionFactory connFactory;

    /**
     * Constructor de producción.
     */
    public AuditService() {
        this(new AuditDAOImpl(), DatabaseConfig::getConnection);
    }

    /**
     * Constructor de paquete para inyección en pruebas.
     */
    AuditService(AuditDAO auditDAO, ConnectionFactory connFactory) {
        this.auditDAO = Objects.requireNonNull(auditDAO, "auditDAO");
        this.connFactory = Objects.requireNonNull(connFactory, "connFactory");
    }

    /**
     * Devuelve un {@code AuditService} sin efecto que descarta silenciosamente todas las escrituras
     * y devuelve resultados vacíos. Útil en contextos de prueba donde se debe evitar la interacción
     * con la base de datos.
     *
     * @return instancia de AuditService sin efecto
     */
    static AuditService noop() {
        return new AuditService(new AuditDAOImpl(), DatabaseConfig::getConnection) {
            @Override
            protected void persistAuditLog(AuditLog auditLog) {
            }

            @Override
            protected List<AuditLog> queryAuditLogs(LocalDateTime fromInclusive,
                                                    LocalDateTime toExclusive,
                                                    String actionFilter,
                                                    int limit) {
                return List.of();
            }
        };
    }

    /**
     * Devuelve los nombres de acción soportados para superficies de filtrado de solo lectura.
     *
     * @return lista ordenada y no modificable de nombres de acción soportados; nunca {@code null}
     */
    public static List<String> supportedActionNames() {
        return SUPPORTED_ACTIONS.stream().sorted().toList();
    }

    /**
     * Registra una compra de asientos completada.
     *
     * @param sale    la venta completada; no debe ser null
     * @param seatIds lista de IDs de asientos comprados; no debe ser null ni estar vacía
     * @throws IllegalArgumentException si {@code sale} o {@code seatIds} es null o está vacío
     */
    public void logPurchaseCompleted(Sale sale, List<Integer> seatIds) {
        if (sale == null) {
            throw new IllegalArgumentException("sale must not be null");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be null or empty");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("seatIds", List.copyOf(seatIds));
        details.put("total", sale.getTotalAmount());
        details.put("boothId", sale.getBoothId());
        writeSafely(
                currentSessionUsername(),
                Action.PURCHASE_SEATS,
                "SALE",
                sale.getSaleId(),
                details
        );
    }

    /**
     * Registra un intento de inicio de sesión exitoso.
     *
     * @param attemptedUsername el nombre de usuario que se autenticó exitosamente; no debe estar en blanco
     */
    public void logLoginSuccess(String attemptedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", "success");
        writeSafely(normalizeRequiredUsername(attemptedUsername), Action.LOGIN_SUCCESS,
                "AUTHENTICATION", null, details);
    }

    /**
     * Registra un intento de inicio de sesión fallido.
     *
     * @param attemptedUsername el nombre de usuario que falló al autenticarse; no debe estar en blanco
     */
    public void logLoginFailure(String attemptedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", "failure");
        writeSafely(normalizeRequiredUsername(attemptedUsername), Action.LOGIN_FAILURE,
                "AUTHENTICATION", null, details);
    }

    /**
     * Registra la creación de una nueva cuenta de usuario.
     *
     * @param adminUsername   nombre de usuario del administrador que creó la cuenta; no debe estar en blanco
     * @param userId          clave primaria del usuario recién creado
     * @param createdUsername nombre de usuario del usuario recién creado; no debe estar en blanco
     * @param role            rol asignado al nuevo usuario; puede ser null
     */
    public void logUserCreated(String adminUsername, int userId, String createdUsername, String role) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", normalizeRequiredUsername(createdUsername));
        details.put("role", normalizeNullableString(role));
        writeSafely(normalizeRequiredUsername(adminUsername), Action.USER_CREATED,
                "USER", userId, details);
    }

    /**
     * Registra una actualización de rol o credenciales de usuario.
     *
     * @param adminUsername administrador que realizó la actualización; no debe estar en blanco
     * @param existingUser  usuario cuyo rol fue cambiado; no debe ser null
     * @param newRole       nuevo rol asignado al usuario; puede ser null
     * @throws IllegalArgumentException si {@code existingUser} es null
     */
    public void logUserRoleUpdated(String adminUsername, User existingUser, String newRole) {
        if (existingUser == null) {
            throw new IllegalArgumentException("existingUser must not be null");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", normalizeRequiredUsername(existingUser.getUsername()));
        details.put("oldRole", normalizeNullableString(existingUser.getRole()));
        details.put("newRole", normalizeNullableString(newRole));
        writeSafely(normalizeRequiredUsername(adminUsername), Action.USER_ROLE_UPDATED,
                "USER", existingUser.getUserId(), details);
    }

    /**
     * Registra la eliminación de una cuenta de usuario.
     *
     * @param adminUsername   administrador que eliminó la cuenta; no debe estar en blanco
     * @param userId          clave primaria del usuario eliminado
     * @param deletedUsername nombre de usuario del usuario eliminado; no debe estar en blanco
     */
    public void logUserDeleted(String adminUsername, int userId, String deletedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", normalizeRequiredUsername(deletedUsername));
        writeSafely(normalizeRequiredUsername(adminUsername), Action.USER_DELETED,
                "USER", userId, details);
    }

    /**
     * Registra la creación de un nuevo evento.
     *
     * @param event el evento creado; no debe ser null
     */
    public void logEventCreated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_CREATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Registra una actualización de detalles de evento.
     *
     * @param event el evento actualizado; no debe ser null
     */
    public void logEventUpdated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_UPDATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Registra la eliminación de un evento.
     *
     * @param event el evento eliminado; no debe ser null
     */
    public void logEventDeleted(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_DELETED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Registra un evento siendo activado para venta de boletos.
     *
     * @param event el evento activado; no debe ser null
     */
    public void logEventActivated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_ACTIVATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Registra un evento siendo desactivado de venta de boletos.
     *
     * @param event el evento desactivado; no debe ser null
     */
    public void logEventDeactivated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_DEACTIVATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Registra la creación de una nueva zona de precios.
     *
     * @param zone la zona creada; no debe ser null
     */
    public void logZoneCreated(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_CREATED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    /**
     * Registra una actualización de detalles de zona.
     *
     * @param zone la zona actualizada; no debe ser null
     */
    public void logZoneUpdated(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_UPDATED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    /**
     * Registra la eliminación de una zona.
     *
     * @param zone la zona eliminada; no debe ser null
     */
    public void logZoneDeleted(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_DELETED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    /**
     * Registra un evento de generación de disposición de asientos.
     *
     * @param zoneId    ID de la zona donde se generaron los asientos
     * @param rowNumber identificador de fila (p.ej. "A"); puede ser null
     * @param fromSeat  primer número de asiento en el rango generado (inclusivo)
     * @param toSeat    último número de asiento en el rango generado (inclusivo)
     */
    public void logSeatsGenerated(int zoneId, String rowNumber, int fromSeat, int toSeat) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rowNumber", normalizeNullableString(rowNumber));
        details.put("fromSeat", fromSeat);
        details.put("toSeat", toSeat);
        details.put("generatedCount", toSeat - fromSeat + 1);
        writeSafely(currentSessionUsername(), Action.SEATS_GENERATED, "ZONE",
                zoneId, details);
    }

    /**
     * Registra la eliminación de una disposición de asientos.
     *
     * @param seatIds lista de IDs de asientos que fueron eliminados; no debe ser null ni estar vacía
     */
    public void logSeatsDeleted(List<Integer> seatIds) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("seatIds", List.copyOf(seatIds));
        details.put("deletedCount", seatIds.size());
        writeSafely(currentSessionUsername(), Action.SEATS_DELETED, "SEAT",
                null, details);
    }

    /**
     * Registra un cambio de estado de disponibilidad de asiento.
     *
     * @param seatIds      lista de IDs de asientos cuyo estado fue alternado; no debe ser null ni estar vacía
     * @param targetStatus el nuevo estado de asiento aplicado a todos los asientos listados; puede ser null
     */
    public void logSeatStatusUpdated(List<Integer> seatIds, SeatStatus targetStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("seatIds", List.copyOf(seatIds));
        details.put("targetStatus", targetStatus != null ? targetStatus.name() : null);
        details.put("updatedCount", seatIds.size());
        writeSafely(currentSessionUsername(), Action.SEAT_STATUS_UPDATED, "SEAT",
                null, details);
    }

    /**
     * Devuelve datos de auditoría de solo lectura para la superficie de reportes administrativos.
     *
     * @param fromInclusive inicio del rango de tiempo (inclusivo); no debe ser null
     * @param toExclusive   fin del rango de tiempo (exclusivo); no debe ser null y debe ser posterior a {@code fromInclusive}
     * @param actionFilter  nombre de acción para filtrar, o null sin filtro
     * @param usernameFilter nombre de usuario para filtrar (insensible a mayúsculas), o null sin filtro
     * @param limit         número máximo de registros a devolver; debe ser positivo
     * @return lista de entradas de registro de auditoría coincidentes, ordenadas por timestamp descendente
     * @throws SQLException     si la consulta a la base de datos falla
     * @throws SecurityException si el usuario de sesión actual no tiene rol ADMIN
     */
    public List<AuditLog> getAuditLogs(LocalDateTime fromInclusive, LocalDateTime toExclusive,
                                       String actionFilter, String usernameFilter, int limit)
            throws SQLException {
        requireAdminRole();
        validateTimeRange(fromInclusive, toExclusive, limit);
        String normalizedAction = normalizeActionFilter(actionFilter);
        String normalizedUsername = normalizeNullableString(usernameFilter);
        List<AuditLog> primaryQuery = queryAuditLogs(fromInclusive, toExclusive, normalizedAction, limit);
        if (normalizedUsername == null) {
            return primaryQuery;
        }
        return primaryQuery.stream()
                .filter(log -> log.getUsername() != null
                        && log.getUsername().equalsIgnoreCase(normalizedUsername))
                .collect(Collectors.toList());
    }

    /**
     * Persiste una entrada de registro de auditoría en la base de datos.
     * Sobreescrita por la variante sin efecto para suprimir escrituras en contextos de prueba.
     *
     * @param auditLog la entrada de auditoría a persistir; no debe ser null
     * @throws SQLException si el insert en la base de datos falla
     */
    protected void persistAuditLog(AuditLog auditLog) throws SQLException {
        try (Connection conn = connFactory.get()) {
            auditDAO.insert(conn, auditLog);
        }
    }

    /**
     * Consulta entradas de registro de auditoría desde la base de datos.
     * Sobreescrita por la variante sin efecto para devolver resultados vacíos en contextos de prueba.
     *
     * @param fromInclusive inicio del rango de tiempo (inclusivo)
     * @param toExclusive   fin del rango de tiempo (exclusivo)
     * @param actionFilter  nombre de acción para filtrar, o null para todas las acciones
     * @param limit         número máximo de registros a devolver
     * @return lista de entradas de registro de auditoría
     * @throws SQLException si la consulta a la base de datos falla
     */
    protected List<AuditLog> queryAuditLogs(LocalDateTime fromInclusive,
                                            LocalDateTime toExclusive,
                                            String actionFilter,
                                            int limit) throws SQLException {
        try (Connection conn = connFactory.get()) {
            if (actionFilter == null) {
                return auditDAO.findRecent(conn, fromInclusive, toExclusive, limit);
            }
            return auditDAO.findRecentByAction(conn, fromInclusive, toExclusive, actionFilter, limit);
        }
    }

    private void writeSafely(String username, Action action, String entityType,
                             Integer entityId, Map<String, Object> details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setUsername(username);
        auditLog.setAction(action.name());
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setDetails(toJson(details));
        try {
            persistAuditLog(auditLog);
        } catch (Exception ex) {
            LOGGER.error("Failed to write audit log action={} entityType={} entityId={} username={}",
                    action.name(), entityType, entityId, username, ex);
        }
    }

    private static Map<String, Object> eventDetails(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", normalizeNullableString(event.getName()));
        details.put("venue", normalizeNullableString(event.getVenue()));
        details.put("eventDate", event.getEventDate() != null ? event.getEventDate().toString() : null);
        details.put("active", event.isActive());
        return details;
    }

    private static Map<String, Object> zoneDetails(Zone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", zone.getEventId());
        details.put("name", normalizeNullableString(zone.getName()));
        details.put("price", zone.getPrice());
        return details;
    }

    private static void validateTimeRange(LocalDateTime fromInclusive,
                                          LocalDateTime toExclusive, int limit) {
        if (fromInclusive == null || toExclusive == null) {
            throw new IllegalArgumentException("time range must not be null");
        }
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be before toExclusive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    private static String normalizeActionFilter(String actionFilter) {
        String normalized = normalizeNullableString(actionFilter);
        if (normalized == null) {
            return null;
        }
        if (!SUPPORTED_ACTIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported action filter: " + normalized);
        }
        return normalized;
    }

    private static String normalizeNullableString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String normalizeRequiredUsername(String username) {
        String normalized = normalizeNullableString(username);
        if (normalized == null) {
            throw new IllegalArgumentException("username must not be null or blank");
        }
        return normalized;
    }

    private static String currentSessionUsername() {
        return SessionContext.getCurrentUser()
                .map(User::getUsername)
                .map(AuditService::normalizeRequiredUsername)
                .orElseThrow(() -> new IllegalStateException("No user is logged in for audit recording"));
    }

    private static void requireAdminRole() {
        if (!SessionContext.hasRole("ADMIN")) {
            throw new SecurityException("Access denied: ADMIN role required");
        }
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return '"' + escapeJson(stringValue) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return '"' + escapeJson(enumValue.name()) + '"';
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> '"' + escapeJson(String.valueOf(entry.getKey())) + "\":" + toJson(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(AuditService::toJson)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        throw new IllegalArgumentException("Unsupported audit JSON value type: " + value.getClass().getName());
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }
}
