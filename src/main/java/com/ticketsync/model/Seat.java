package com.ticketsync.model;

import java.util.Objects;

/**
 * Representa un asiento individual dentro de una zona con estado de disponibilidad.
 * Mapea a la tabla de base de datos 'seats'.
 * 
 * <h2>Transiciones de Estado</h2>
 * <ul>
 *   <li>AVAILABLE → SOLD (transacción atómica)</li>
 *   <li>AVAILABLE ↔ DISABLED (cambio de administrador)</li>
 *   <li>RESERVED (mejora futura, no MVP)</li>
 * </ul>
 * 
 * <h2>Control de Concurrencia</h2>
 * Las actualizaciones de estado de asientos usan bloqueo pesimista (SELECT FOR UPDATE) en transacciones
 * SERIALIZABLE para prevenir la sobreventar. Ver {@link com.ticketsync.dao.SeatDAO#selectForUpdate}.
 * 
 * @see com.ticketsync.dao.SeatDAO
 * @see SeatStatus
 * @see Zone
 */
public class Seat {
    /** Clave primaria de la columna seats.seat_id. */
    private int seatId;
    
    /** Clave foránea a zones.zone_id. */
    private int zoneId;
    
    /** Identificador de fila (ej., "A", "12", "Main"). */
    private String rowNumber;
    
    /** Número de asiento dentro de la fila (ej., "1", "23B"). */
    private String seatNumber;
    
    /** Estado actual de disponibilidad. */
    private SeatStatus status;
    
    /** Clave foránea a sales.sale_id (null si no está vendido). */
    private Integer saleId;

    /** ID (como texto) del vendor que mantiene la reserva; null si no está reservado. */
    private String reservedBy;

    /**
     * Constructor por defecto para mapeo JDBC.
     */
    public Seat() {
    }
    
    /**
     * Construye un Seat con todos los campos.
     * 
     * @param seatId Clave primaria
     * @param zoneId Zona a la que pertenece este asiento
     * @param rowNumber Identificador de fila
     * @param seatNumber Número de asiento dentro de la fila
     * @param status Estado de disponibilidad
     * @param saleId ID de venta si fue vendido, null en caso contrario
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
    
    // Getters y Setters

    /**
     * Devuelve el ID del asiento.
     *
     * @return el ID del asiento
     */
    public int getSeatId() {
        return seatId;
    }

    /**
     * Establece el ID del asiento.
     *
     * @param seatId el ID del asiento
     */
    public void setSeatId(int seatId) {
        this.seatId = seatId;
    }

    /**
     * Devuelve el ID de zona al que pertenece este asiento.
     *
     * @return el ID de zona
     */
    public int getZoneId() {
        return zoneId;
    }

    /**
     * Establece el ID de zona al que pertenece este asiento.
     *
     * @param zoneId el ID de zona
     */
    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Devuelve la etiqueta de fila para este asiento.
     *
     * @return la etiqueta de fila; nunca {@code null}
     */
    public String getRowNumber() {
        return rowNumber;
    }

    /**
     * Establece la etiqueta de fila para este asiento.
     *
     * @param rowNumber la etiqueta de fila; no debe ser {@code null} ni estar en blanco
     * @throws IllegalArgumentException si {@code rowNumber} es {@code null} o está en blanco
     */
    public void setRowNumber(String rowNumber) {
        if (rowNumber == null || rowNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("rowNumber cannot be null or empty");
        }
        this.rowNumber = rowNumber;
    }
    
    /**
     * Devuelve el número de asiento dentro de su fila.
     *
     * @return el número de asiento; nunca {@code null}
     */
    public String getSeatNumber() {
        return seatNumber;
    }

    /**
     * Establece el número de asiento dentro de su fila.
     *
     * @param seatNumber el número de asiento; no debe ser {@code null} ni estar en blanco
     * @throws IllegalArgumentException si {@code seatNumber} es {@code null} o está en blanco
     */
    public void setSeatNumber(String seatNumber) {
        if (seatNumber == null || seatNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("seatNumber cannot be null or empty");
        }
        this.seatNumber = seatNumber;
    }
    
    /**
     * Devuelve el estado actual de reserva del asiento.
     *
     * @return el estado del asiento; nunca {@code null}
     */
    public SeatStatus getStatus() {
        return status;
    }

    /**
     * Establece el estado de reserva del asiento.
     *
     * @param status el nuevo estado; no debe ser {@code null}
     * @throws IllegalArgumentException si {@code status} es {@code null}
     */
    public void setStatus(SeatStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        this.status = status;
    }
    
    /**
     * Devuelve el ID de venta asociado con este asiento, o {@code null} si no fue vendido.
     *
     * @return el ID de venta; puede ser {@code null}
     */
    public Integer getSaleId() {
        return saleId;
    }

    /**
     * Establece el ID de venta asociado con este asiento.
     *
     * @param saleId el ID de venta; puede ser {@code null} para indicar que el asiento no está vendido
     */
    public void setSaleId(Integer saleId) {
        this.saleId = saleId;
    }

    /**
     * Devuelve el identificador del vendor que tiene reservado este asiento,
     * o {@code null} si el asiento no está actualmente reservado por nadie.
     *
     * @return el ID del vendor reservante; puede ser {@code null}
     */
    public String getReservedBy() {
        return reservedBy;
    }

    /**
     * Establece el identificador del vendor que reserva este asiento.
     *
     * @param reservedBy el ID del vendor; puede ser {@code null} para borrar la reserva
     */
    public void setReservedBy(String reservedBy) {
        this.reservedBy = reservedBy;
    }

    // Métodos de Utilidad
    
    /**
     * Compara asientos basado en la clave primaria.
     * 
     * @param o Objeto a comparar
     * @return true si tienen el mismo seatId, false en caso contrario
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
     * Hash basado en la clave primaria.
     * 
     * @return Código hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(seatId);
    }
    
    /**
     * Representación en cadena para depuración.
     * 
     * @return Representación en cadena
     */
    @Override
    public String toString() {
        return "Seat{seatId=" + seatId + ", zoneId=" + zoneId + ", rowNumber='" + rowNumber
                + "', seatNumber='" + seatNumber + "', status=" + status + ", saleId=" + saleId + "}";
    }
}
