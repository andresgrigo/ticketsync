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
        /** Seats successfully purchased in a single atomic transaction. */
        PURCHASE_SEATS,
        /** User authenticated successfully. */
        LOGIN_SUCCESS,
        /** Login attempt failed (wrong credentials or unknown user). */
        LOGIN_FAILURE,
        /** New user account created by an administrator. */
        USER_CREATED,
        /** Existing user role or credentials updated by an administrator. */
        USER_ROLE_UPDATED,
        /** User account deleted by an administrator. */
        USER_DELETED,
        /** New event created by an administrator. */
        EVENT_CREATED,
        /** Existing event details edited by an administrator. */
        EVENT_UPDATED,
        /** Event deleted by an administrator. */
        EVENT_DELETED,
        /** Event enabled for ticket sales by an administrator. */
        EVENT_ACTIVATED,
        /** Event disabled from ticket sales by an administrator. */
        EVENT_DEACTIVATED,
        /** New pricing zone created for an event. */
        ZONE_CREATED,
        /** Existing zone details edited. */
        ZONE_UPDATED,
        /** Zone deleted from an event. */
        ZONE_DELETED,
        /** Seat layout generated (rows and seats assigned to a zone). */
        SEATS_GENERATED,
        /** Seat layout deleted for a zone. */
        SEATS_DELETED,
        /** Individual seat availability toggled by an administrator. */
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

    /**
     * Returns a no-op {@code AuditService} that silently discards all writes and returns empty results.
     * Useful for testing contexts where database interaction must be avoided.
     *
     * @return no-op AuditService instance
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
     * Returns supported action names for read-only filtering surfaces.
     *
     * @return sorted, unmodifiable list of supported action names; never {@code null}
     */
    public static List<String> supportedActionNames() {
        return SUPPORTED_ACTIONS.stream().sorted().toList();
    }

    /**
     * Records a completed seat purchase.
     *
     * @param sale    the completed sale; must not be null
     * @param seatIds list of purchased seat IDs; must not be null or empty
     * @throws IllegalArgumentException if {@code sale} or {@code seatIds} is null or empty
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
     * Records a successful login attempt.
     *
     * @param attemptedUsername the username that successfully authenticated; must not be blank
     */
    public void logLoginSuccess(String attemptedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", "success");
        writeSafely(normalizeRequiredUsername(attemptedUsername), Action.LOGIN_SUCCESS,
                "AUTHENTICATION", null, details);
    }

    /**
     * Records a failed login attempt.
     *
     * @param attemptedUsername the username that failed to authenticate; must not be blank
     */
    public void logLoginFailure(String attemptedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", "failure");
        writeSafely(normalizeRequiredUsername(attemptedUsername), Action.LOGIN_FAILURE,
                "AUTHENTICATION", null, details);
    }

    /**
     * Records a new user account creation.
     *
     * @param adminUsername   username of the administrator who created the account; must not be blank
     * @param userId          primary key of the newly created user
     * @param createdUsername username of the newly created user; must not be blank
     * @param role            role assigned to the new user; may be null
     */
    public void logUserCreated(String adminUsername, int userId, String createdUsername, String role) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", normalizeRequiredUsername(createdUsername));
        details.put("role", normalizeNullableString(role));
        writeSafely(normalizeRequiredUsername(adminUsername), Action.USER_CREATED,
                "USER", userId, details);
    }

    /**
     * Records a user role or credential update.
     *
     * @param adminUsername administrator who performed the update; must not be blank
     * @param existingUser  user whose role was changed; must not be null
     * @param newRole       new role assigned to the user; may be null
     * @throws IllegalArgumentException if {@code existingUser} is null
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
     * Records a user account deletion.
     *
     * @param adminUsername   administrator who deleted the account; must not be blank
     * @param userId          primary key of the deleted user
     * @param deletedUsername username of the deleted user; must not be blank
     */
    public void logUserDeleted(String adminUsername, int userId, String deletedUsername) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", normalizeRequiredUsername(deletedUsername));
        writeSafely(normalizeRequiredUsername(adminUsername), Action.USER_DELETED,
                "USER", userId, details);
    }

    /**
     * Records a new event creation.
     *
     * @param event the created event; must not be null
     */
    public void logEventCreated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_CREATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Records an event detail update.
     *
     * @param event the updated event; must not be null
     */
    public void logEventUpdated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_UPDATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Records an event deletion.
     *
     * @param event the deleted event; must not be null
     */
    public void logEventDeleted(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_DELETED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Records an event being activated for ticket sales.
     *
     * @param event the activated event; must not be null
     */
    public void logEventActivated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_ACTIVATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Records an event being deactivated from ticket sales.
     *
     * @param event the deactivated event; must not be null
     */
    public void logEventDeactivated(Event event) {
        writeSafely(currentSessionUsername(), Action.EVENT_DEACTIVATED, "EVENT",
                event.getEventId(), eventDetails(event));
    }

    /**
     * Records a new pricing zone creation.
     *
     * @param zone the created zone; must not be null
     */
    public void logZoneCreated(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_CREATED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    /**
     * Records a zone detail update.
     *
     * @param zone the updated zone; must not be null
     */
    public void logZoneUpdated(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_UPDATED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    /**
     * Records a zone deletion.
     *
     * @param zone the deleted zone; must not be null
     */
    public void logZoneDeleted(Zone zone) {
        writeSafely(currentSessionUsername(), Action.ZONE_DELETED, "ZONE",
                zone.getZoneId(), zoneDetails(zone));
    }

    /**
     * Records a seat layout generation event.
     *
     * @param zoneId    ID of the zone where seats were generated
     * @param rowNumber row identifier (e.g. "A"); may be null
     * @param fromSeat  first seat number in the generated range (inclusive)
     * @param toSeat    last seat number in the generated range (inclusive)
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
     * Records a seat layout deletion.
     *
     * @param seatIds list of seat IDs that were deleted; must not be null or empty
     */
    public void logSeatsDeleted(List<Integer> seatIds) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("seatIds", List.copyOf(seatIds));
        details.put("deletedCount", seatIds.size());
        writeSafely(currentSessionUsername(), Action.SEATS_DELETED, "SEAT",
                null, details);
    }

    /**
     * Records a seat availability status change.
     *
     * @param seatIds      list of seat IDs whose status was toggled; must not be null or empty
     * @param targetStatus the new seat status applied to all listed seats; may be null
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
     * Returns read-only audit data for the admin reporting surface.
     *
     * @param fromInclusive start of the time range (inclusive); must not be null
     * @param toExclusive   end of the time range (exclusive); must not be null and must be after {@code fromInclusive}
     * @param actionFilter  action name to filter by, or null for no filter
     * @param usernameFilter username to filter by (case-insensitive), or null for no filter
     * @param limit         maximum number of records to return; must be positive
     * @return list of matching audit log entries, sorted by timestamp descending
     * @throws SQLException     if the database query fails
     * @throws SecurityException if the current session user does not have ADMIN role
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
     * Persists an audit log entry to the database.
     * Overridden by the no-op variant to suppress writes in test contexts.
     *
     * @param auditLog the audit entry to persist; must not be null
     * @throws SQLException if the database insert fails
     */
    protected void persistAuditLog(AuditLog auditLog) throws SQLException {
        try (Connection conn = connFactory.get()) {
            auditDAO.insert(conn, auditLog);
        }
    }

    /**
     * Queries audit log entries from the database.
     * Overridden by the no-op variant to return empty results in test contexts.
     *
     * @param fromInclusive start of the time range (inclusive)
     * @param toExclusive   end of the time range (exclusive)
     * @param actionFilter  action name to filter by, or null for all actions
     * @param limit         maximum number of records to return
     * @return list of matching audit log entries
     * @throws SQLException if the database query fails
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
