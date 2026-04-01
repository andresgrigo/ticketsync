package com.ticketsync.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a pricing zone within an event's seating layout.
 * Maps to the 'zones' database table.
 * 
 * <h2>Zone Concept</h2>
 * Each event is divided into zones with different pricing tiers (e.g., "Floor", "Balcony", "VIP").
 * All seats within a zone have the same ticket price.
 * 
 * <h2>Example</h2>
 * <pre>
 * Event: Spring Concert 2024
 * - Zone 1: "VIP Floor" ($150.00)
 * - Zone 2: "General Floor" ($90.00)
 * - Zone 3: "Balcony" ($50.00)
 * </pre>
 * 
 * @see com.ticketsync.dao.ZoneDAO
 * @see Seat
 */
public class Zone {
    /** Primary key from zones.zone_id column. */
    private int zoneId;
    
    /** Foreign key to events.event_id. */
    private int eventId;
    
    /** Zone name (e.g., "Floor", "Balcony", "VIP"). */
    private String name;
    
    /** Ticket price for this zone (all seats same price). */
    private BigDecimal price;
    
    /**
     * Default constructor for JDBC mapping.
     */
    public Zone() {
    }
    
    /**
     * Constructs a Zone with all fields.
     * 
     * @param zoneId Primary key
     * @param eventId Event this zone belongs to
     * @param name Zone name
     * @param price Ticket price for this zone
     */
    public Zone(int zoneId, int eventId, String name, BigDecimal price) {
        this.zoneId = zoneId;
        this.eventId = eventId;
        this.name = name;
        this.price = price;
    }
    
    // Getters and Setters
    
    public int getZoneId() {
        return zoneId;
    }
    
    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }
    
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
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("price cannot be null");
        }
        this.price = price;
    }
    
    // Utility Methods
    
    /**
     * Compares zones based on primary key.
     * 
     * @param o Object to compare
     * @return true if same zoneId, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Zone zone = (Zone) o;
        return zoneId == zone.zoneId;
    }
    
    /**
     * Hash based on primary key.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(zoneId);
    }
    
    /**
     * String representation for debugging.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "Zone{zoneId=" + zoneId + ", eventId=" + eventId + ", name='" + name
                + "', price=" + price + "}";
    }
}
