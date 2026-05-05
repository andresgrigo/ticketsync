package com.ticketsync.exception;

/**
 * Thrown when ticket PDF generation cannot complete because required sale data
 * cannot be loaded or the PDF cannot be rendered safely.
 */
public class TicketGenerationException extends Exception {

    /**
     * Constructs an exception with the given detail message.
     *
     * @param message human-readable description of the failure
     */
    public TicketGenerationException(String message) {
        super(message);
    }

    /**
     * Constructs an exception with the given detail message and cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying exception that caused the failure
     */
    public TicketGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
