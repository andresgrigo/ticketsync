package com.ticketsync.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a completed ticket sale transaction.
 * Maps to the 'sales' database table.
 * 
 * <h2>Transaction Workflow</h2>
 * <ol>
 *   <li>Vendor selects seats in POS interface</li>
 *   <li>System locks seats with SELECT FOR UPDATE</li>
 *   <li>System creates Sale record (this class)</li>
 *   <li>System creates SaleItem records linking seats to sale</li>
 *   <li>System updates seat status to SOLD</li>
 *   <li>Transaction commits atomically</li>
 * </ol>
 * 
 * <h2>Transaction ID Format</h2>
 * Generated transaction IDs follow pattern: TXN-YYYYMMDD-HHMMSS-BX
 * Example: TXN-20240315-143022-B1
 * 
 * @see com.ticketsync.dao.SaleDAO
 * @see SaleItem
 */
public class Sale {
    /** Primary key from sales.sale_id column. */
    private int saleId;
    
    /** Foreign key to events.event_id. */
    private int eventId;
    
    /** Foreign key to users.user_id (vendor who made sale). */
    private int vendorId;
    
    /** Sum of all seat prices in this sale. */
    private BigDecimal totalAmount;
    
    /** Timestamp when sale was completed. */
    private LocalDateTime saleTimestamp;
    
    /** Booth identifier (e.g., "Booth-1", "Booth-2"). */
    private String boothId;
    
    /**
     * Default constructor for JDBC mapping.
     */
    public Sale() {
    }
    
    /**
     * Constructs a Sale with all fields.
     * 
     * @param saleId Primary key
     * @param eventId Event for which tickets were sold
     * @param vendorId Vendor who made the sale
     * @param totalAmount Total sale amount
     * @param saleTimestamp Sale completion timestamp
     * @param boothId Booth identifier
     */
    public Sale(int saleId, int eventId, int vendorId, BigDecimal totalAmount,
            LocalDateTime saleTimestamp, String boothId) {
        this.saleId = saleId;
        this.eventId = eventId;
        this.vendorId = vendorId;
        this.totalAmount = totalAmount;
        this.saleTimestamp = saleTimestamp;
        this.boothId = boothId;
    }
    
    // Getters and Setters
    
    public int getSaleId() {
        return saleId;
    }
    
    public void setSaleId(int saleId) {
        this.saleId = saleId;
    }
    
    public int getEventId() {
        return eventId;
    }
    
    public void setEventId(int eventId) {
        this.eventId = eventId;
    }
    
    public int getVendorId() {
        return vendorId;
    }
    
    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        if (totalAmount == null) {
            throw new IllegalArgumentException("totalAmount cannot be null");
        }
        this.totalAmount = totalAmount;
    }
    
    public LocalDateTime getSaleTimestamp() {
        return saleTimestamp;
    }
    
    public void setSaleTimestamp(LocalDateTime saleTimestamp) {
        this.saleTimestamp = saleTimestamp;
    }
    
    public String getBoothId() {
        return boothId;
    }
    
    public void setBoothId(String boothId) {
        this.boothId = boothId;
    }
    
    // Utility Methods
    
    /**
     * Compares sales based on primary key.
     * 
     * @param o Object to compare
     * @return true if same saleId, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Sale sale = (Sale) o;
        return saleId == sale.saleId;
    }
    
    /**
     * Hash based on primary key.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(saleId);
    }
    
    /**
     * String representation for debugging.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "Sale{saleId=" + saleId + ", eventId=" + eventId + ", vendorId=" + vendorId
                + ", totalAmount=" + totalAmount + ", saleTimestamp=" + saleTimestamp
                + ", boothId='" + boothId + "'}";
    }
}
