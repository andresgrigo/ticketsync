package com.ticketsync.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a ticketed event with date, venue, and activation status.
 * Maps to the 'events' database table.
 * 
 * <h2>Event Lifecycle</h2>
 * Events are created by administrators with {@code isActive = true} to appear in vendor POS.
 * Deactivating an event ({@code isActive = false}) hides it from vendor event selector
 * but preserves historical sales data.
 * 
 * @see com.ticketsync.dao.EventDAO
 */
public class Event {
    /** Primary key from events.event_id column. */
    private int eventId;
    
    /** Event name (e.g., "Spring Concert 2024"). */
    private String name;
    
    /** Date and time when event occurs. */
    private LocalDateTime eventDate;
    
    /** Venue name and location. */
    private String venue;
    
    /** Detailed event description. */
    private String description;
    
    /** Whether event appears in vendor POS event selector. */
    private boolean isActive;
    
    /** User ID of administrator who created this event. */
    private int createdBy;
    
    /** Timestamp when event was created in system. */
    private LocalDateTime createdAt;
    
    /**
     * Default constructor for JDBC mapping.
     */
    public Event() {
    }
    
    /**
     * Constructs an Event with all fields.
     * 
     * @param eventId Primary key
     * @param name Event name
     * @param eventDate Event date and time
     * @param venue Venue name
     * @param description Event description
     * @param isActive Whether event is active
     * @param createdBy User ID who created event
     * @param createdAt Creation timestamp
     */
    public Event(int eventId, String name, LocalDateTime eventDate, String venue,
            String description, boolean isActive, int createdBy, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.name = name;
        this.eventDate = eventDate;
        this.venue = venue;
        this.description = description;
        this.isActive = isActive;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
    
    // Getters and Setters

    /**
     * Returns the event ID.
     *
     * @return the event ID
     */
    public int getEventId() {
        return eventId;
    }

    /**
     * Sets the event ID.
     *
     * @param eventId the event ID
     */
    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    /**
     * Returns the event name.
     *
     * @return the event name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the event name.
     *
     * @param name the event name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the event date and time.
     *
     * @return the event date and time; may be {@code null}
     */
    public LocalDateTime getEventDate() {
        return eventDate;
    }

    /**
     * Sets the event date and time.
     *
     * @param eventDate the event date and time
     */
    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    /**
     * Returns the venue name.
     *
     * @return the venue name; may be {@code null}
     */
    public String getVenue() {
        return venue;
    }

    /**
     * Sets the venue name.
     *
     * @param venue the venue name
     */
    public void setVenue(String venue) {
        this.venue = venue;
    }

    /**
     * Returns the event description.
     *
     * @return the event description; may be {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the event description.
     *
     * @param description the event description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns whether the event is active.
     *
     * @return {@code true} if active, {@code false} otherwise
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Sets the active state of the event.
     *
     * @param isActive {@code true} to activate the event, {@code false} to deactivate
     */
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    /**
     * Returns the ID of the user who created the event.
     *
     * @return the creator user ID
     */
    public int getCreatedBy() {
        return createdBy;
    }

    /**
     * Sets the ID of the user who created the event.
     *
     * @param createdBy the creator user ID
     */
    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Returns the timestamp when the event was created.
     *
     * @return the creation timestamp; may be {@code null}
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the creation timestamp
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Utility Methods
    
    /**
     * Compares events based on primary key.
     * 
     * @param o Object to compare
     * @return true if same eventId, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Event event = (Event) o;
        return eventId == event.eventId;
    }
    
    /**
     * Hash based on primary key.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
    
    /**
     * String representation for debugging.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "Event{eventId=" + eventId + ", name='" + name + "', eventDate=" + eventDate
                + ", venue='" + venue + "', isActive=" + isActive + "}";
    }
}
