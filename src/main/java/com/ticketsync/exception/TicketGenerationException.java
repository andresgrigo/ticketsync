package com.ticketsync.exception;

/**
 * Thrown when ticket PDF generation cannot complete because required sale data
 * cannot be loaded or the PDF cannot be rendered safely.
 */
public class TicketGenerationException extends Exception {

    public TicketGenerationException(String message) {
        super(message);
    }

    public TicketGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
