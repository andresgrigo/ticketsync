package com.ticketsync.exception;

/**
 * Lanzada cuando la generación de PDF de boleto no puede completarse porque los datos
 * de venta requeridos no pueden cargarse o el PDF no puede renderizarse de forma segura.
 */
public class TicketGenerationException extends Exception {

    /**
     * Construye una excepción con el mensaje de detalle dado.
     *
     * @param message descripción legible por humanos del fallo
     */
    public TicketGenerationException(String message) {
        super(message);
    }

    /**
     * Construye una excepción con el mensaje de detalle y causa dados.
     *
     * @param message descripción legible por humanos del fallo
     * @param cause   la excepción subyacente que causó el fallo
     */
    public TicketGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
