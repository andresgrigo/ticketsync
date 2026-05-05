package com.ticketsync.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa un asiento individual dentro de una transacción de venta.
 * Mapea a la tabla de base de datos 'sale_items'.
 * 
 * <h2>Tabla de Unión</h2>
 * Esta clase representa la relación muchos-a-muchos entre Sales y Seats.
 * Cada SaleItem vincula un asiento a una venta, registrando el precio pagado al momento de la compra.
 * 
 * <h2>Historial de Precios</h2>
 * El campo {@code pricePaid} preserva el historial de precios incluso si los precios de zona cambian después.
 * Esto garantiza reportes financieros precisos y rastros de auditoría.
 * 
 * @see com.ticketsync.dao.SaleDAO
 * @see Sale
 * @see Seat
 */
public class SaleItem {
    /** Clave primaria de la columna sale_items.sale_item_id. */
    private int saleItemId;
    
    /** Clave foránea a sales.sale_id. */
    private int saleId;
    
    /** Clave foránea a seats.seat_id. */
    private int seatId;
    
    /** Precio al momento de la compra (registro histórico). */
    private BigDecimal pricePaid;
    
    /**
     * Constructor por defecto para mapeo JDBC.
     */
    public SaleItem() {
    }
    
    /**
     * Construye un SaleItem con todos los campos.
     * 
     * @param saleItemId Clave primaria
     * @param saleId Venta a la que pertenece este ítem
     * @param seatId Asiento que fue vendido
     * @param pricePaid Precio pagado por este asiento
     */
    public SaleItem(int saleItemId, int saleId, int seatId, BigDecimal pricePaid) {
        this.saleItemId = saleItemId;
        this.saleId = saleId;
        this.seatId = seatId;
        this.pricePaid = pricePaid;
    }
    
    // Getters y Setters

    /**
     * Devuelve el ID del ítem de venta.
     *
     * @return el ID del ítem de venta
     */
    public int getSaleItemId() {
        return saleItemId;
    }

    /**
     * Establece el ID del ítem de venta.
     *
     * @param saleItemId el ID del ítem de venta
     */
    public void setSaleItemId(int saleItemId) {
        this.saleItemId = saleItemId;
    }

    /**
     * Devuelve el ID de la venta a la que pertenece este ítem.
     *
     * @return el ID de la venta
     */
    public int getSaleId() {
        return saleId;
    }

    /**
     * Establece el ID de la venta a la que pertenece este ítem.
     *
     * @param saleId el ID de la venta
     */
    public void setSaleId(int saleId) {
        this.saleId = saleId;
    }

    /**
     * Devuelve el ID del asiento asociado a este ítem de venta.
     *
     * @return el ID del asiento
     */
    public int getSeatId() {
        return seatId;
    }

    /**
     * Establece el ID del asiento asociado a este ítem de venta.
     *
     * @param seatId el ID del asiento
     */
    public void setSeatId(int seatId) {
        this.seatId = seatId;
    }

    /**
     * Devuelve el precio pagado por este asiento.
     *
     * @return el precio pagado; nunca {@code null}
     */
    public BigDecimal getPricePaid() {
        return pricePaid;
    }

    /**
     * Establece el precio pagado por este asiento.
     *
     * @param pricePaid el precio pagado
     */
    public void setPricePaid(BigDecimal pricePaid) {
        this.pricePaid = pricePaid;
    }
    
    // Métodos de Utilidad
    
    /**
     * Compara ítems de venta basado en la clave primaria.
     * 
     * @param o Objeto a comparar
     * @return true si tienen el mismo saleItemId, false en caso contrario
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
     * Hash basado en la clave primaria.
     * 
     * @return Código hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(saleItemId);
    }
    
    /**
     * Representación en cadena para depuración.
     * 
     * @return Representación en cadena
     */
    @Override
    public String toString() {
        return "SaleItem{saleItemId=" + saleItemId + ", saleId=" + saleId + ", seatId=" + seatId
                + ", pricePaid=" + pricePaid + "}";
    }
}
