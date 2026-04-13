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
 * Service for business audit writes and read-only audit queries.
 */
public class AuditService {

    /**
     * Stable audit action names used across service-layer business events.
     */
    public enum Action {
        PURCHASE_SEATS,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        USER_CREATED,
        USER_ROLE_UPDATED,
        USER_DELETED,
        EVENT_CREATED,
        EVENT_UPDATED,
        EVENT_DELETED,
        EVENT_ACTIVATED,
        EVENT_DEACTIVATED,
        ZONE_CREATED,
        ZONE_UPDATED,
        ZONE_DELETED,
        SEATS_GENERATED,
        SEATS_DELETED,
        SEAT_STATUS_UPDATED
    }

    private static final Logger LOGGER = LogManager.getLogger(AuditService.class);
    private static final Set<String> SUPPORTED_ACTIONS = Arrays.stream(Action.values())
            .map(Action::name)
            .collect(Collectors.toUnmodifiableSet());

    private final AuditDAO auditDAO;
    private final ConnectionFactory connFactory;

    /**
     * Production constructor.
     */
    public AuditService() {
        this(new AuditDAOImpl(), DatabaseConfig::getConnection);
    }

    /**
     * Package-private constructor for test injection.
     */
    AuditService(AuditDAO auditDAO, ConnectionFactory connFactory) {
        this.auditDAO = Objects.requireNonNull(auditDAO, "auditDAO");
        this.connFactory = Objects.requireNonNull(connFactory, "connFactory");
    }

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
     * Returns supported action names for read-only filtering surfaces.
     */
    public static List<String> supportedActionNames() {
        return SUPPORTED_ACTIONS.stream().sorted().toList();
    }

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

    public void logLoginSuccess(String attemptedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", "success");
        writeSafely(normalizeRequiredUsername(attemptedUsername), Action.LOGIN_SUCCESS,
                "AUTHENTICATION", null, details);
    }

    public void logLoginFailure(String attemptedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", "failure");
        writeSafely(normalizeRequiredUsername(attemptedUsername), Action.LOGIN_FAILURE,
                "AUTHENTICATION", null, details);
    }

    public void logUserCreated(String adminUsername, int userId, String createdUsername, String role) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", normalizeRequiredUsername(createdUsername));
        details.put("role", normalizeNullableString(role));
        writeSafely(normalizeRequiredUsername(adminUsername), Action.USER_CREATED,
                "USER", userId, details);
    }

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

    public void logUserDeleted(String adminUsername, int userId, String deletedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", normalizeRequiredUsername(deletedUsername));
        writeSafely(normalizeRequiredUsername(adminUsername), Action.USER_DELETED,
                "USER", userId, details);
    }

    public void logEventCreated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_CREATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    public void logEventUpdated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_UPDATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    public void logEventDeleted(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_DELETED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    public void logEventActivated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_ACTIVATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    public void logEventDeactivated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_DEACTIVATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    public void logZoneCreated(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_CREATED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    public void logZoneUpdated(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_UPDATED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    public void logZoneDeleted(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_DELETED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    public void logSeatsGenerated(int zoneId, String rowNumber, int fromSeat, int toSeat) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rowNumber", normalizeNullableString(rowNumber));
        details.put("fromSeat", fromSeat);
        details.put("toSeat", toSeat);
        details.put("generatedCount", toSeat - fromSeat + 1);
        writeSafely(currentSessionUsername(), Action.SEATS_GENERATED, "ZONE",
                zoneId, details);
    }

    public void logSeatsDeleted(List<Integer> seatIds) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("seatIds", List.copyOf(seatIds));
        details.put("deletedCount", seatIds.size());
        writeSafely(currentSessionUsername(), Action.SEATS_DELETED, "SEAT",
                null, details);
    }

    public void logSeatStatusUpdated(List<Integer> seatIds, SeatStatus targetStatus) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("seatIds", List.copyOf(seatIds));
        details.put("targetStatus", targetStatus != null ? targetStatus.name() : null);
        details.put("updatedCount", seatIds.size());
        writeSafely(currentSessionUsername(), Action.SEAT_STATUS_UPDATED, "SEAT",
                null, details);
    }

    /**
     * Returns read-only audit data for the admin reporting surface.
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

    protected void persistAuditLog(AuditLog auditLog) throws SQLException {
        try (Connection conn = connFactory.get()) {
            auditDAO.insert(conn, auditLog);
        }
    }

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
