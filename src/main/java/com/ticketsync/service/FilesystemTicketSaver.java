package com.ticketsync.service;

import com.ticketsync.model.Sale;
import com.ticketsync.util.FilePathUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Persiste PDFs de boletos ya generados en el sistema de archivos local cuando la impresión no está disponible.
 */
public class FilesystemTicketSaver {

    private static final Logger LOGGER = LogManager.getLogger(FilesystemTicketSaver.class);

    private final Path ticketsRootDirectory;

    /**
     * Crea un nuevo {@code FilesystemTicketSaver} que escribe en el directorio
     * resuelto por {@link FilePathUtil#getTicketsDirectory()}.
     */
    public FilesystemTicketSaver() {
        this(FilePathUtil.getTicketsDirectory());
    }

    /**
     * Crea un nuevo {@code FilesystemTicketSaver} que escribe en el directorio dado.
     *
     * @param ticketsRootDirectory directorio raíz para PDFs de boletos; no debe ser {@code null}
     */
    public FilesystemTicketSaver(Path ticketsRootDirectory) {
        this.ticketsRootDirectory = Objects.requireNonNull(ticketsRootDirectory, "ticketsRootDirectory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Devuelve el directorio raíz donde se guardan los PDFs de boletos.
     *
     * @return directorio raíz de boletos absoluto y normalizado; nunca {@code null}
     */
    public Path getTicketsRootDirectory() {
        return ticketsRootDirectory;
    }

    /**
     * Guarda un PDF de boleto para la venta dada en un subdirectorio con fecha.
     *
     * <p>Crea el subdirectorio de fecha si aún no existe y escribe
     * {@code pdfData} como un nuevo archivo nombrado según el ID de transacción.
     *
     * @param sale    la venta completada; no debe ser {@code null}
     * @param pdfData los bytes PDF a persistir; no debe ser {@code null} ni estar vacío
     * @return la ruta del archivo PDF recién creado
     * @throws IOException              si el archivo no puede escribirse
     * @throws IllegalArgumentException si {@code pdfData} está vacío
     */
    public Path saveTicket(Sale sale, byte[] pdfData) throws IOException {
        Objects.requireNonNull(sale, "sale must not be null");
        Objects.requireNonNull(pdfData, "pdfData must not be null");
        if (pdfData.length == 0) {
            throw new IllegalArgumentException("pdfData must not be empty");
        }

        LocalDateTime saleTimestamp = Objects.requireNonNull(sale.getSaleTimestamp(), "saleTimestamp must not be null");
        String transactionId = PurchaseReceiptDetails.formatTransactionId(sale);
        Path datedDirectory = FilePathUtil.ensureDirectoryExists(
                ticketsRootDirectory.resolve(saleTimestamp.toLocalDate().toString())
        );

        Path targetPath = datedDirectory.resolve(transactionId + ".pdf").normalize();
        Files.write(targetPath, pdfData, StandardOpenOption.CREATE_NEW);
        LOGGER.info("Ticket saved to {} - Print manually from file (transactionId={}, saleId={})",
                targetPath, transactionId, sale.getSaleId());
        return targetPath;
    }
}
