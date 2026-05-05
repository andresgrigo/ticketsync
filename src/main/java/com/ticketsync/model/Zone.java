package com.ticketsync.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa una zona de precios dentro del plano de asientos de un evento.
 * Mapea a la tabla de base de datos 'zones'.
 * 
 * <h2>Concepto de Zona</h2>
 * Cada evento se divide en zonas con diferentes niveles de precios (ej., "Pista", "Balcón", "VIP").
 * Todos los asientos dentro de una zona tienen el mismo precio de entrada.
 * 
 * <h2>Ejemplo</h2>
 * <pre>
 * Evento: Concierto de Primavera 2024
 * - Zona 1: "VIP Pista" ($150.00)
 * - Zona 2: "Pista General" ($90.00)
 * - Zona 3: "Balcón" ($50.00)
 * </pre>
 * 
 * @see com.ticketsync.dao.ZoneDAO
 * @see Seat
 */
public class Zone {
    /** Clave primaria de la columna zones.zone_id. */
    private int zoneId;
    
    /** Clave foránea a events.event_id. */
    private int eventId;
    
    /** Nombre de la zona (ej., "Pista", "Balcón", "VIP"). */
    private String name;
    
    /** Precio de la entrada para esta zona (todos los asientos tienen el mismo precio). */
    private BigDecimal price;
    
    /**
     * Constructor por defecto para mapeo JDBC.
     */
    public Zone() {
    }
    
    /**
     * Construye una Zone con todos los campos.
     * 
     * @param zoneId Clave primaria
     * @param eventId Evento al que pertenece esta zona
     * @param name Nombre de la zona
     * @param price Precio de entrada para esta zona
     */
    public Zone(int zoneId, int eventId, String name, BigDecimal price) {
        this.zoneId = zoneId;
        this.eventId = eventId;
        this.name = name;
        this.price = price;
    }
    
    // Getters y Setters

    /**
     * Devuelve el identificador de zona.
     *
     * @return clave primaria generada por la base de datos
     */
    public int getZoneId() {
        return zoneId;
    }

    /**
     * Establece el identificador de zona.
     *
     * @param zoneId clave primaria generada por la base de datos
     */
    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Devuelve el evento al que pertenece esta zona.
     *
     * @return clave foránea referenciando el evento padre
     */
    public int getEventId() {
        return eventId;
    }

    /**
     * Establece el evento al que pertenece esta zona.
     *
     * @param eventId clave foránea referenciando el evento padre
     */
    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    /**
     * Devuelve el nombre de la zona.
     *
     * @return cadena con el nombre de la zona (ej., {@code "Pista"}, {@code "VIP"})
     */
    public String getName() {
        return name;
    }

    /**
     * Establece el nombre de la zona.
     *
     * @param name nombre de la zona; no debe ser null
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Devuelve el precio de entrada para esta zona.
     *
     * @return precio de entrada; todos los asientos de la zona comparten este precio
     */
    public BigDecimal getPrice() {
        return price;
    }
    
    /**
     * Establece el precio de entrada para esta zona.
     *
     * @param price precio de entrada; no debe ser null
     */
    public void setPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("price cannot be null");
        }
        this.price = price;
    }
    
    // Métodos de Utilidad
    
    /**
     * Compara zonas basado en la clave primaria.
     * 
     * @param o Objeto a comparar
     * @return true si tienen el mismo zoneId, false en caso contrario
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
     * Hash basado en la clave primaria.
     * 
     * @return Código hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(zoneId);
    }
    
    /**
     * Representación en cadena para depuración.
     * 
     * @return Representación en cadena
     */
    @Override
    public String toString() {
        return "Zone{zoneId=" + zoneId + ", eventId=" + eventId + ", name='" + name
                + "', price=" + price + "}";
    }
}
