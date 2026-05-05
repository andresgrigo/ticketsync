package com.ticketsync.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when one or more seats are unavailable for purchase (status != AVAILABLE)
 * or when a concurrent transaction causes a serialization conflict.
 */
public class SeatUnavailableException extends Exception {

    /** Seat IDs that were unavailable or caused the serialization conflict. */
    private final List<Integer> unavailableSeatIds;

    /**
     * For seat validation failures — seats exist but are not AVAILABLE.
     *
     * @param message            human-readable description
     * @param unavailableSeatIds the seat IDs that failed validation
     */
    public SeatUnavailableException(String message, List<Integer> unavailableSeatIds) {
        super(message);
        this.unavailableSeatIds = unavailableSeatIds != null
                ? Collections.unmodifiableList(new ArrayList<>(unavailableSeatIds))
                : Collections.emptyList();
    }

    /**
     * For SERIALIZABLE serialization failures — wraps a database exception.
     *
     * @param message human-readable description
     * @param seatIds the seat IDs involved in the failed transaction
     * @param cause   the underlying {@link java.sql.SQLException}
     */
    public SeatUnavailableException(String message, List<Integer> seatIds, Throwable cause) {
        super(message, cause);
        this.unavailableSeatIds = seatIds != null
                ? Collections.unmodifiableList(new ArrayList<>(seatIds))
                : Collections.emptyList();
    }

    /**
     * Returns the seat IDs that were unavailable or involved in the conflict.
     *
     * @return unmodifiable list of seat IDs; never {@code null}
     */
    public List<Integer> getUnavailableSeatIds() {
        return unavailableSeatIds;
    }
}
