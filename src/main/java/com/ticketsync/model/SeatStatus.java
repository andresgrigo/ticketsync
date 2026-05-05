package com.ticketsync.model;

/**
 * Estado de disponibilidad del asiento para sincronización en tiempo real entre puestos.
 * 
 * <h2>Transiciones de Estado</h2>
 * <ul>
 *   <li>AVAILABLE → SOLD (transacción atómica)</li>
 *   <li>AVAILABLE ↔ DISABLED (alternancia de administrador)</li>
 *   <li>RESERVED (mejora futura, no en MVP)</li>
 * </ul>
 * 
 * <h2>Códigos de Color</h2>
 * <ul>
 *   <li>AVAILABLE: Verde (puede ser comprado)</li>
 *   <li>SOLD: Rojo (comprado por cualquier puesto)</li>
 *   <li>RESERVED: Amarillo (bloqueado durante transacción, tiempo de espera de 60 segundos)</li>
 *   <li>DISABLED: Gris (deshabilitado administrativamente, no seleccionable)</li>
 * </ul>
 * 
 * @see Seat
 */
public enum SeatStatus {
    /**
     * El asiento está disponible para su compra.
     */
    AVAILABLE,
    
    /**
     * El asiento ha sido vendido y no puede ser comprado.
     */
    SOLD,
    
    /**
     * El asiento está temporalmente bloqueado durante una transacción (mejora futura).
     */
    RESERVED,
    
    /**
     * El asiento está deshabilitado administrativamente y no está disponible para su compra.
     */
    DISABLED
}
