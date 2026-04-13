package com.ticketsync.model;

/**
 * Seat availability status for real-time synchronization across booths.
 * 
 * <h2>Status Transitions</h2>
 * <ul>
 *   <li>AVAILABLE → SOLD (atomic transaction)</li>
 *   <li>AVAILABLE ↔ DISABLED (admin toggle)</li>
 *   <li>RESERVED (future enhancement, not MVP)</li>
 * </ul>
 * 
 * <h2>Color Coding</h2>
 * <ul>
 *   <li>AVAILABLE: Green (can be purchased)</li>
 *   <li>SOLD: Red (purchased by any booth)</li>
 *   <li>RESERVED: Yellow (locked during transaction, 60-second timeout)</li>
 *   <li>DISABLED: Gray (administratively disabled, not selectable)</li>
 * </ul>
 * 
 * @see Seat
 */
public enum SeatStatus {
    /**
     * Seat is available for purchase.
     */
    AVAILABLE,
    
    /**
     * Seat has been sold and cannot be purchased.
     */
    SOLD,
    
    /**
     * Seat is temporarily locked during a transaction (future enhancement).
     */
    RESERVED,
    
    /**
     * Seat is administratively disabled and not available for purchase.
     */
    DISABLED
}
