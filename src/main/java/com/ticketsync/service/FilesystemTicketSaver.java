package com.ticketsync.service;

import com.ticketsync.model.Sale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Persists already-generated ticket PDFs to the local filesystem when printing is unavailable.
 */
public class FilesystemTicketSaver {

    private static final Logger LOGGER = LogManager.getLogger(FilesystemTicketSaver.class);

    private final Path ticketsRootDirectory;

    public FilesystemTicketSaver() {
        this(Path.of("tickets"));
    }

    public FilesystemTicketSaver(Path ticketsRootDirectory) {
        this.ticketsRootDirectory = Objects.requireNonNull(ticketsRootDirectory, "ticketsRootDirectory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    public Path getTicketsRootDirectory() {
        return ticketsRootDirectory;
    }

    public Path saveTicket(Sale sale, byte[] pdfData) throws IOException {
        Objects.requireNonNull(sale, "sale must not be null");
        Objects.requireNonNull(pdfData, "pdfData must not be null");
        if (pdfData.length == 0) {
            throw new IllegalArgumentException("pdfData must not be empty");
        }

        LocalDateTime saleTimestamp = Objects.requireNonNull(sale.getSaleTimestamp(), "saleTimestamp must not be null");
        String transactionId = PurchaseReceiptDetails.formatTransactionId(sale);
        Path datedDirectory = ticketsRootDirectory.resolve(saleTimestamp.toLocalDate().toString());
        Files.createDirectories(datedDirectory);

        Path targetPath = datedDirectory.resolve(transactionId + ".pdf").normalize();
        Files.write(targetPath, pdfData, StandardOpenOption.CREATE_NEW);
        LOGGER.info("Ticket saved to {} - Print manually from file (transactionId={}, saleId={})",
                targetPath, transactionId, sale.getSaleId());
        return targetPath;
    }
}
