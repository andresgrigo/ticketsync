package com.ticketsync.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a single seat within a sale transaction.
 * Maps to the 'sale_items' database table.
 * 
 * <h2>Junction Table</h2>
 * This class represents the many-to-many relationship between Sales and Seats.
 * Each SaleItem links one seat to one sale, recording the price paid at time of purchase.
 * 
 * <h2>Price History</h2>
 * The {@code pricePaid} field preserves historical pricing even if zone prices change later.
 * This ensures accurate financial reporting and audit trails.
 * 
 * @see com.ticketsync.dao.SaleDAO
 * @see Sale
 * @see Seat
 */
public class SaleItem {
    /** Primary key from sale_items.sale_item_id column. */
    private int saleItemId;
    
    /** Foreign key to sales.sale_id. */
    private int saleId;
    
    /** Foreign key to seats.seat_id. */
    private int seatId;
    
    /** Price at time of purchase (historical record). */
    private BigDecimal pricePaid;
    
    /**
     * Default constructor for JDBC mapping.
     */
    public SaleItem() {
    }
    
    /**
     * Constructs a SaleItem with all fields.
     * 
     * @param saleItemId Primary key
     * @param saleId Sale this item belongs to
     * @param seatId Seat that was sold
     * @param pricePaid Price paid for this seat
     */
    public SaleItem(int saleItemId, int saleId, int seatId, BigDecimal pricePaid) {
        this.saleItemId = saleItemId;
        this.saleId = saleId;
        this.seatId = seatId;
        this.pricePaid = pricePaid;
    }
    
    // Getters and Setters

    /**
     * Returns the sale item ID.
     *
     * @return the sale item ID
     */
    public int getSaleItemId() {
        return saleItemId;
    }

    /**
     * Sets the sale item ID.
     *
     * @param saleItemId the sale item ID
     */
    public void setSaleItemId(int saleItemId) {
        this.saleItemId = saleItemId;
    }

    /**
     * Returns the sale ID this item belongs to.
     *
     * @return the sale ID
     */
    public int getSaleId() {
        return saleId;
    }

    /**
     * Sets the sale ID this item belongs to.
     *
     * @param saleId the sale ID
     */
    public void setSaleId(int saleId) {
        this.saleId = saleId;
    }

    /**
     * Returns the seat ID associated with this sale item.
     *
     * @return the seat ID
     */
    public int getSeatId() {
        return seatId;
    }

    /**
     * Sets the seat ID associated with this sale item.
     *
     * @param seatId the seat ID
     */
    public void setSeatId(int seatId) {
        this.seatId = seatId;
    }

    /**
     * Returns the price paid for this seat.
     *
     * @return the price paid; never {@code null}
     */
    public BigDecimal getPricePaid() {
        return pricePaid;
    }

    /**
     * Sets the price paid for this seat.
     *
     * @param pricePaid the price paid
     */
    public void setPricePaid(BigDecimal pricePaid) {
        this.pricePaid = pricePaid;
    }
    
    // Utility Methods
    
    /**
     * Compares sale items based on primary key.
     * 
     * @param o Object to compare
     * @return true if same saleItemId, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SaleItem saleItem = (SaleItem) o;
        return saleItemId == saleItem.saleItemId;
    }
    
    /**
     * Hash based on primary key.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(saleItemId);
    }
    
    /**
     * String representation for debugging.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "SaleItem{saleItemId=" + saleItemId + ", saleId=" + saleId + ", seatId=" + seatId
                + ", pricePaid=" + pricePaid + "}";
    }
}
