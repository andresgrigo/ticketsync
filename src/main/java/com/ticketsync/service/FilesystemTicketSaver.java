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
 * Persists already-generated ticket PDFs to the local filesystem when printing is unavailable.
 */
public class FilesystemTicketSaver {

    private static final Logger LOGGER = LogManager.getLogger(FilesystemTicketSaver.class);

    private final Path ticketsRootDirectory;

    /**
     * Creates a new {@code FilesystemTicketSaver} that writes to the directory
     * resolved by {@link FilePathUtil#getTicketsDirectory()}.
     */
    public FilesystemTicketSaver() {
        this(FilePathUtil.getTicketsDirectory());
    }

    /**
     * Creates a new {@code FilesystemTicketSaver} that writes to the given directory.
     *
     * @param ticketsRootDirectory root directory for ticket PDFs; must not be {@code null}
     */
    public FilesystemTicketSaver(Path ticketsRootDirectory) {
        this.ticketsRootDirectory = Objects.requireNonNull(ticketsRootDirectory, "ticketsRootDirectory must not be null")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Returns the root directory where ticket PDFs are saved.
     *
     * @return absolute, normalised tickets root directory; never {@code null}
     */
    public Path getTicketsRootDirectory() {
        return ticketsRootDirectory;
    }

    /**
     * Saves a ticket PDF for the given sale to a dated subdirectory.
     *
     * <p>Creates the date subdirectory if it does not yet exist and writes
     * {@code pdfData} as a new file named after the transaction ID.
     *
     * @param sale    the completed sale; must not be {@code null}
     * @param pdfData the PDF bytes to persist; must not be {@code null} or empty
     * @return the path of the newly created PDF file
     * @throws IOException              if the file cannot be written
     * @throws IllegalArgumentException if {@code pdfData} is empty
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
