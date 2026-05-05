package com.ticketsync.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lanzada cuando uno o más asientos no están disponibles para su compra (status != AVAILABLE)
 * o cuando una transacción concurrente causa un conflicto de serialización.
 */
public class SeatUnavailableException extends Exception {

    /** IDs de asientos que no estaban disponibles o causaron el conflicto de serialización. */
    private final List<Integer> unavailableSeatIds;

    /**
     * Para fallos de validación de asientos — los asientos existen pero no están DISPONIBLES.
     *
     * @param message            descripción legible por humanos
     * @param unavailableSeatIds los IDs de asientos que fallaron la validación
     */
    public SeatUnavailableException(String message, List<Integer> unavailableSeatIds) {
        super(message);
        this.unavailableSeatIds = unavailableSeatIds != null
                ? Collections.unmodifiableList(new ArrayList<>(unavailableSeatIds))
                : Collections.emptyList();
    }

    /**
     * Para fallos de serialización SERIALIZABLE — envuelve una excepción de base de datos.
     *
     * @param message descripción legible por humanos
     * @param seatIds los IDs de asientos involucrados en la transacción fallida
     * @param cause   la excepción subyacente {@link java.sql.SQLException}
     */
    public SeatUnavailableException(String message, List<Integer> seatIds, Throwable cause) {
        super(message, cause);
        this.unavailableSeatIds = seatIds != null
                ? Collections.unmodifiableList(new ArrayList<>(seatIds))
                : Collections.emptyList();
    }

    /**
     * Devuelve los IDs de asientos que no estaban disponibles o estuvieron involucrados en el conflicto.
     *
     * @return lista no modificable de IDs de asientos; nunca {@code null}
     */
    public List<Integer> getUnavailableSeatIds() {
        return unavailableSeatIds;
    }
}
