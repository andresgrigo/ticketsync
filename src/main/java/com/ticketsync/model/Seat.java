package com.ticketsync.model;

import java.util.Objects;

/**
 * Represents a single seat within a zone with availability status.
 * Maps to the 'seats' database table.
 * 
 * <h2>Status Transitions</h2>
 * <ul>
 *   <li>AVAILABLE → SOLD (atomic transaction)</li>
 *   <li>AVAILABLE ↔ DISABLED (admin toggle)</li>
 *   <li>RESERVED (future enhancement, not MVP)</li>
 * </ul>
 * 
 * <h2>Concurrency Control</h2>
 * Seat status updates use pessimistic locking (SELECT FOR UPDATE) in SERIALIZABLE transactions
 * to prevent overselling. See {@link com.ticketsync.dao.SeatDAO#selectForUpdate}.
 * 
 * @see com.ticketsync.dao.SeatDAO
 * @see SeatStatus
 * @see Zone
 */
public class Seat {
    /** Primary key from seats.seat_id column. */
    private int seatId;
    
    /** Foreign key to zones.zone_id. */
    private int zoneId;
    
    /** Row identifier (e.g., "A", "12", "Main"). */
    private String rowNumber;
    
    /** Seat number within row (e.g., "1", "23B"). */
    private String seatNumber;
    
    /** Current availability status. */
    private SeatStatus status;
    
    /** Foreign key to sales.sale_id (null if not sold). */
    private Integer saleId;
    
    /**
     * Default constructor for JDBC mapping.
     */
    public Seat() {
    }
    
    /**
     * Constructs a Seat with all fields.
     * 
     * @param seatId Primary key
     * @param zoneId Zone this seat belongs to
     * @param rowNumber Row identifier
     * @param seatNumber Seat number within row
     * @param status Availability status
     * @param saleId Sale ID if sold, null otherwise
     */
    public Seat(int seatId, int zoneId, String rowNumber, String seatNumber,
            SeatStatus status, Integer saleId) {
        this.seatId = seatId;
        this.zoneId = zoneId;
        this.rowNumber = rowNumber;
        this.seatNumber = seatNumber;
        this.status = status;
        this.saleId = saleId;
    }
    
    // Getters and Setters

    /**
     * Returns the seat ID.
     *
     * @return the seat ID
     */
    public int getSeatId() {
        return seatId;
    }

    /**
     * Sets the seat ID.
     *
     * @param seatId the seat ID
     */
    public void setSeatId(int seatId) {
        this.seatId = seatId;
    }

    /**
     * Returns the zone ID this seat belongs to.
     *
     * @return the zone ID
     */
    public int getZoneId() {
        return zoneId;
    }

    /**
     * Sets the zone ID this seat belongs to.
     *
     * @param zoneId the zone ID
     */
    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Returns the row label for this seat.
     *
     * @return the row label; never {@code null}
     */
    public String getRowNumber() {
        return rowNumber;
    }

    /**
     * Sets the row label for this seat.
     *
     * @param rowNumber the row label; must not be {@code null} or blank
     * @throws IllegalArgumentException if {@code rowNumber} is {@code null} or blank
     */
    public void setRowNumber(String rowNumber) {
        if (rowNumber == null || rowNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("rowNumber cannot be null or empty");
        }
        this.rowNumber = rowNumber;
    }
    
    /**
     * Returns the seat number within its row.
     *
     * @return the seat number; never {@code null}
     */
    public String getSeatNumber() {
        return seatNumber;
    }

    /**
     * Sets the seat number within its row.
     *
     * @param seatNumber the seat number; must not be {@code null} or blank
     * @throws IllegalArgumentException if {@code seatNumber} is {@code null} or blank
     */
    public void setSeatNumber(String seatNumber) {
        if (seatNumber == null || seatNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("seatNumber cannot be null or empty");
        }
        this.seatNumber = seatNumber;
    }
    
    /**
     * Returns the current booking status of the seat.
     *
     * @return the seat status; never {@code null}
     */
    public SeatStatus getStatus() {
        return status;
    }

    /**
     * Sets the booking status of the seat.
     *
     * @param status the new status; must not be {@code null}
     * @throws IllegalArgumentException if {@code status} is {@code null}
     */
    public void setStatus(SeatStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        this.status = status;
    }
    
    /**
     * Returns the sale ID associated with this seat, or {@code null} if not sold.
     *
     * @return the sale ID; may be {@code null}
     */
    public Integer getSaleId() {
        return saleId;
    }

    /**
     * Sets the sale ID associated with this seat.
     *
     * @param saleId the sale ID; may be {@code null} to indicate the seat is not sold
     */
    public void setSaleId(Integer saleId) {
        this.saleId = saleId;
    }
    
    // Utility Methods
    
    /**
     * Compares seats based on primary key.
     * 
     * @param o Object to compare
     * @return true if same seatId, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Seat seat = (Seat) o;
        return seatId == seat.seatId;
    }
    
    /**
     * Hash based on primary key.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(seatId);
    }
    
    /**
     * String representation for debugging.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "Seat{seatId=" + seatId + ", zoneId=" + zoneId + ", rowNumber='" + rowNumber
                + "', seatNumber='" + seatNumber + "', status=" + status + ", saleId=" + saleId + "}";
    }
}
