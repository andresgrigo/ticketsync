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
    
    public int getEventId() {
        return eventId;
    }
    
    public void setEventId(int eventId) {
        this.eventId = eventId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public LocalDateTime getEventDate() {
        return eventDate;
    }
    
    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }
    
    public String getVenue() {
        return venue;
    }
    
    public void setVenue(String venue) {
        this.venue = venue;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }
    
    public int getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
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
