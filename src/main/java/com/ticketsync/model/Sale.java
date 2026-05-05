package com.ticketsync.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa una transacción de venta de tickets completada.
 * Mapea a la tabla de base de datos 'sales'.
 * 
 * <h2>Flujo de Transacción</h2>
 * <ol>
 *   <li>El vendedor selecciona asientos en la interfaz POS</li>
 *   <li>El sistema bloquea los asientos con SELECT FOR UPDATE</li>
 *   <li>El sistema crea el registro Sale (esta clase)</li>
 *   <li>El sistema crea registros SaleItem vinculando asientos a la venta</li>
 *   <li>El sistema actualiza el estado del asiento a SOLD</li>
 *   <li>La transacción se confirma atómicamente</li>
 * </ol>
 * 
 * <h2>Formato de ID de Transacción</h2>
 * Los IDs de transacción generados siguen el patrón: TXN-YYYYMMDD-HHMMSS-BX
 * Ejemplo: TXN-20240315-143022-B1
 * 
 * @see com.ticketsync.dao.SaleDAO
 * @see SaleItem
 */
public class Sale {
    /** Clave primaria de la columna sales.sale_id. */
    private int saleId;
    
    /** Clave foránea a events.event_id. */
    private int eventId;
    
    /** Clave foránea a users.user_id (vendedor que realizó la venta). */
    private int vendorId;
    
    /** Suma de todos los precios de asientos en esta venta. */
    private BigDecimal totalAmount;
    
    /** Marca de tiempo de cuando se completó la venta. */
    private LocalDateTime saleTimestamp;
    
    /** Identificador del puesto (ej., "Booth-1", "Booth-2"). */
    private String boothId;
    
    /**
     * Constructor por defecto para mapeo JDBC.
     */
    public Sale() {
    }
    
    /**
     * Construye una Sale con todos los campos.
     * 
     * @param saleId Clave primaria
     * @param eventId Evento para el que se vendieron los tickets
     * @param vendorId Vendedor que realizó la venta
     * @param totalAmount Monto total de la venta
     * @param saleTimestamp Marca de tiempo de finalización de la venta
     * @param boothId Identificador del puesto
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
    
    // Getters y Setters

    /**
     * Devuelve el ID de la venta.
     *
     * @return el ID de la venta
     */
    public int getSaleId() {
        return saleId;
    }

    /**
     * Establece el ID de la venta.
     *
     * @param saleId el ID de la venta
     */
    public void setSaleId(int saleId) {
        this.saleId = saleId;
    }

    /**
     * Devuelve el ID del evento asociado a esta venta.
     *
     * @return el ID del evento
     */
    public int getEventId() {
        return eventId;
    }

    /**
     * Establece el ID del evento asociado a esta venta.
     *
     * @param eventId el ID del evento
     */
    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    /**
     * Devuelve el ID de usuario del vendedor que registró esta venta.
     *
     * @return el ID del vendedor
     */
    public int getVendorId() {
        return vendorId;
    }

    /**
     * Establece el ID de usuario del vendedor que registró esta venta.
     *
     * @param vendorId el ID del vendedor
     */
    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }

    /**
     * Devuelve el monto total de la venta.
     *
     * @return el monto total; nunca {@code null}
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Establece el monto total de la venta.
     *
     * @param totalAmount el monto total; no debe ser {@code null}
     * @throws IllegalArgumentException si {@code totalAmount} es {@code null}
     */
    public void setTotalAmount(BigDecimal totalAmount) {
        if (totalAmount == null) {
            throw new IllegalArgumentException("totalAmount cannot be null");
        }
        this.totalAmount = totalAmount;
    }
    
    /**
     * Devuelve la marca de tiempo de cuando se registró la venta.
     *
     * @return la marca de tiempo de la venta; puede ser {@code null}
     */
    public LocalDateTime getSaleTimestamp() {
        return saleTimestamp;
    }

    /**
     * Establece la marca de tiempo de cuando se registró la venta.
     *
     * @param saleTimestamp la marca de tiempo de la venta
     */
    public void setSaleTimestamp(LocalDateTime saleTimestamp) {
        this.saleTimestamp = saleTimestamp;
    }

    /**
     * Devuelve el ID del puesto donde se registró la venta.
     *
     * @return el ID del puesto; puede ser {@code null}
     */
    public String getBoothId() {
        return boothId;
    }

    /**
     * Establece el ID del puesto donde se registró la venta.
     *
     * @param boothId el ID del puesto
     */
    public void setBoothId(String boothId) {
        this.boothId = boothId;
    }
    
    // Métodos de Utilidad
    
    /**
     * Compara ventas basado en la clave primaria.
     * 
     * @param o Objeto a comparar
     * @return true si tienen el mismo saleId, false en caso contrario
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
     * Hash basado en la clave primaria.
     * 
     * @return Código hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(saleId);
    }
    
    /**
     * Representación en cadena para depuración.
     * 
     * @return Representación en cadena
     */
    @Override
    public String toString() {
        return "Sale{saleId=" + saleId + ", eventId=" + eventId + ", vendorId=" + vendorId
                + ", totalAmount=" + totalAmount + ", saleTimestamp=" + saleTimestamp
                + ", boothId='" + boothId + "'}";
    }
}
