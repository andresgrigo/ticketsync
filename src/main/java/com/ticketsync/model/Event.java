package com.ticketsync.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa un evento con boletos que incluye fecha, lugar y estado de activación.
 * Mapea a la tabla de base de datos 'events'.
 * 
 * <h2>Ciclo de Vida del Evento</h2>
 * Los eventos son creados por administradores con {@code isActive = true} para aparecer en el POS del proveedor.
 * Desactivar un evento ({@code isActive = false}) lo oculta del selector de eventos del proveedor
 * pero preserva los datos históricos de ventas.
 * 
 * @see com.ticketsync.dao.EventDAO
 */
public class Event {
    /** Clave primaria de la columna events.event_id. */
    private int eventId;
    
    /** Nombre del evento (ej., "Concierto de Primavera 2024"). */
    private String name;
    
    /** Fecha y hora en que ocurre el evento. */
    private LocalDateTime eventDate;
    
    /** Nombre y ubicación del lugar del evento. */
    private String venue;
    
    /** Descripción detallada del evento. */
    private String description;
    
    /** Si el evento aparece en el selector de eventos del POS del proveedor. */
    private boolean isActive;
    
    /** ID de usuario del administrador que creó este evento. */
    private int createdBy;
    
    /** Marca de tiempo de cuando el evento fue creado en el sistema. */
    private LocalDateTime createdAt;
    
    /**
     * Constructor por defecto para mapeo JDBC.
     */
    public Event() {
    }
    
    /**
     * Construye un Event con todos los campos.
     * 
     * @param eventId Clave primaria
     * @param name Nombre del evento
     * @param eventDate Fecha y hora del evento
     * @param venue Nombre del lugar
     * @param description Descripción del evento
     * @param isActive Si el evento está activo
     * @param createdBy ID de usuario que creó el evento
     * @param createdAt Marca de tiempo de creación
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
    
    // Getters y Setters

    /**
     * Devuelve el ID del evento.
     *
     * @return el ID del evento
     */
    public int getEventId() {
        return eventId;
    }

    /**
     * Establece el ID del evento.
     *
     * @param eventId el ID del evento
     */
    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    /**
     * Devuelve el nombre del evento.
     *
     * @return el nombre del evento
     */
    public String getName() {
        return name;
    }

    /**
     * Establece el nombre del evento.
     *
     * @param name el nombre del evento
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Devuelve la fecha y hora del evento.
     *
     * @return la fecha y hora del evento; puede ser {@code null}
     */
    public LocalDateTime getEventDate() {
        return eventDate;
    }

    /**
     * Establece la fecha y hora del evento.
     *
     * @param eventDate la fecha y hora del evento
     */
    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    /**
     * Devuelve el nombre del lugar del evento.
     *
     * @return el nombre del lugar; puede ser {@code null}
     */
    public String getVenue() {
        return venue;
    }

    /**
     * Establece el nombre del lugar del evento.
     *
     * @param venue el nombre del lugar
     */
    public void setVenue(String venue) {
        this.venue = venue;
    }

    /**
     * Devuelve la descripción del evento.
     *
     * @return la descripción del evento; puede ser {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Establece la descripción del evento.
     *
     * @param description la descripción del evento
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Devuelve si el evento está activo.
     *
     * @return {@code true} si está activo, {@code false} en caso contrario
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Establece el estado activo del evento.
     *
     * @param isActive {@code true} para activar el evento, {@code false} para desactivarlo
     */
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    /**
     * Devuelve el ID del usuario que creó el evento.
     *
     * @return el ID del usuario creador
     */
    public int getCreatedBy() {
        return createdBy;
    }

    /**
     * Establece el ID del usuario que creó el evento.
     *
     * @param createdBy el ID del usuario creador
     */
    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Devuelve la marca de tiempo de cuando fue creado el evento.
     *
     * @return la marca de tiempo de creación; puede ser {@code null}
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Establece la marca de tiempo de creación.
     *
     * @param createdAt la marca de tiempo de creación
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Métodos de Utilidad
    
    /**
     * Compara eventos basado en la clave primaria.
     * 
     * @param o Objeto a comparar
     * @return true si tienen el mismo eventId, false en caso contrario
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
     * Hash basado en la clave primaria.
     * 
     * @return Código hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
    
    /**
     * Representación en cadena para depuración.
     * 
     * @return Representación en cadena
     */
    @Override
    public String toString() {
        return "Event{eventId=" + eventId + ", name='" + name + "', eventDate=" + eventDate
                + ", venue='" + venue + "', isActive=" + isActive + "}";
    }
}
