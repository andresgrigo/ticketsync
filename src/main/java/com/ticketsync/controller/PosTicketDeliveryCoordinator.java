package com.ticketsync.controller;

import com.ticketsync.exception.TicketGenerationException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

final class PosTicketDeliveryCoordinator {

    private static final Logger LOGGER = LogManager.getLogger(PosTicketDeliveryCoordinator.class);

    private final SaleItemsLookup saleItemsLookup;
    private final TicketPdfGenerator ticketPdfGenerator;
    private final TicketSaver ticketSaver;

    PosTicketDeliveryCoordinator(
            SaleItemsLookup saleItemsLookup,
            TicketPdfGenerator ticketPdfGenerator,
            TicketSaver ticketSaver
    ) {
        this.saleItemsLookup = Objects.requireNonNull(saleItemsLookup, "saleItemsLookup must not be null");
        this.ticketPdfGenerator = Objects.requireNonNull(ticketPdfGenerator, "ticketPdfGenerator must not be null");
        this.ticketSaver = Objects.requireNonNull(ticketSaver, "ticketSaver must not be null");
    }

    DeliveryOutcome execute(PosPurchaseCoordinator.PurchaseSuccess success) throws SQLException, TicketGenerationException {
        Objects.requireNonNull(success, "success must not be null");

        Sale sale = success.sale();
        String transactionId = success.receiptDetails().transactionId();
        List<SaleItem> saleItems = saleItemsLookup.getSaleItemsBySaleId(sale.getSaleId());
        if (saleItems.isEmpty()) {
            LOGGER.warn("No committed sale items found for manual ticket delivery fallback: saleId={}", sale.getSaleId());
            return new TicketSaveFailed(
                    transactionId,
                    "Ticket data for " + transactionId
                            + " could not be prepared. Please contact support before handing tickets to the customer."
            );
        }

        byte[] pdfData = ticketPdfGenerator.generateTicket(sale, saleItems);
        try {
            Path savedPath = ticketSaver.saveTicket(sale, pdfData);
            return new TicketSavedToFile(transactionId, savedPath);
        } catch (IOException ex) {
            LOGGER.error("Failed to save ticket PDF for {}", transactionId, ex);
            return new TicketSaveFailed(
                    transactionId,
                    "TicketSync could not save the ticket file. "
                            + "Please contact support before handing tickets to the customer."
            );
        }
    }

    sealed interface DeliveryOutcome permits TicketSavedToFile, TicketSaveFailed {
    }

    record TicketSavedToFile(String transactionId, Path savedPath) implements DeliveryOutcome {
        TicketSavedToFile {
            transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
            savedPath = Objects.requireNonNull(savedPath, "savedPath must not be null").toAbsolutePath().normalize();
        }

        String operatorMessage() {
            return "Ticket saved to: "
                    + savedPath
                    + ". Please print manually and hand to customer.";
        }
    }

    record TicketSaveFailed(String transactionId, String operatorMessage) implements DeliveryOutcome {
        TicketSaveFailed {
            transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
            operatorMessage = Objects.requireNonNull(operatorMessage, "operatorMessage must not be null");
        }
    }

    @FunctionalInterface
    interface SaleItemsLookup {
        List<SaleItem> getSaleItemsBySaleId(int saleId) throws SQLException;
    }

    @FunctionalInterface
    interface TicketPdfGenerator {
        byte[] generateTicket(Sale sale, List<SaleItem> saleItems) throws TicketGenerationException;
    }

    @FunctionalInterface
    interface TicketSaver {
        Path saveTicket(Sale sale, byte[] pdfData) throws IOException;
    }
}
