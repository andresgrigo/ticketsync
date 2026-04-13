package com.ticketsync.controller;

import com.ticketsync.exception.TicketGenerationException;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.service.PurchaseReceiptDetails;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosTicketDeliveryCoordinatorTest {

    @Test
    void execute_savesPdfWhenSaleItemsExist() throws Exception {
        StubSaleItemsLookup saleItemsLookup = new StubSaleItemsLookup();
        StubTicketPdfGenerator ticketPdfGenerator = new StubTicketPdfGenerator(new byte[]{1, 2, 3});
        StubTicketSaver ticketSaver = new StubTicketSaver(Path.of("tickets", "2026-04-13", "TXN-20260413-194637-B9.pdf"));
        PosTicketDeliveryCoordinator coordinator = new PosTicketDeliveryCoordinator(
                saleItemsLookup,
                ticketPdfGenerator,
                ticketSaver
        );

        PosTicketDeliveryCoordinator.DeliveryOutcome outcome = coordinator.execute(success());

        PosTicketDeliveryCoordinator.TicketSavedToFile saved =
                assertInstanceOf(PosTicketDeliveryCoordinator.TicketSavedToFile.class, outcome);
        Path expectedSavedPath = ticketSaver.savedPath.toAbsolutePath().normalize();
        assertEquals(expectedSavedPath, saved.savedPath());
        assertArrayEquals(new byte[]{1, 2, 3}, ticketSaver.savedPdfData);
        assertEquals(501, saleItemsLookup.requestedSaleId);
    }

    @Test
    void execute_savesSamePdfBytesForManualPrintingWorkflow() throws Exception {
        StubSaleItemsLookup saleItemsLookup = new StubSaleItemsLookup();
        byte[] pdfBytes = new byte[]{10, 20, 30, 40};
        StubTicketPdfGenerator ticketPdfGenerator = new StubTicketPdfGenerator(pdfBytes);
        StubTicketSaver ticketSaver = new StubTicketSaver(Path.of("tickets", "2026-04-13", "TXN-20260413-194637-B9.pdf"));
        PosTicketDeliveryCoordinator coordinator = new PosTicketDeliveryCoordinator(
                saleItemsLookup,
                ticketPdfGenerator,
                ticketSaver
        );

        PosTicketDeliveryCoordinator.DeliveryOutcome outcome = coordinator.execute(success());

        PosTicketDeliveryCoordinator.TicketSavedToFile saved =
                assertInstanceOf(PosTicketDeliveryCoordinator.TicketSavedToFile.class, outcome);
        Path expectedSavedPath = ticketSaver.savedPath.toAbsolutePath().normalize();
        assertEquals(expectedSavedPath, saved.savedPath());
        assertEquals(
            "Ticket saved to: "
                        + expectedSavedPath
                        + ". Please print manually and hand to customer.",
                saved.operatorMessage()
        );
        assertArrayEquals(pdfBytes, ticketSaver.savedPdfData);
        assertEquals(501, saleItemsLookup.requestedSaleId);
    }

    @Test
    void execute_returnsSaveFailedWhenNoSaleItemsFound() throws Exception {
        PosTicketDeliveryCoordinator coordinator = new PosTicketDeliveryCoordinator(
                saleId -> List.of(),
                new StubTicketPdfGenerator(new byte[]{1, 2, 3}),
                new StubTicketSaver(Path.of("unused.pdf"))
        );

        PosTicketDeliveryCoordinator.DeliveryOutcome outcome = coordinator.execute(success());

        PosTicketDeliveryCoordinator.TicketSaveFailed failed =
                assertInstanceOf(PosTicketDeliveryCoordinator.TicketSaveFailed.class, outcome);
        assertEquals("TXN-20260413-194637-B9", failed.transactionId());
        assertTrue(
                failed.operatorMessage().contains("TXN-20260413-194637-B9"),
                "Failure message should include the transaction ID"
        );
        assertTrue(
                failed.operatorMessage().contains("contact support"),
                "Failure message should direct operator to contact support"
        );
    }

    @Test
    void execute_returnsSaveFailedWhenFallbackWriteFails() throws Exception {
        PosTicketDeliveryCoordinator coordinator = new PosTicketDeliveryCoordinator(
                new StubSaleItemsLookup(),
                new StubTicketPdfGenerator(new byte[]{9, 8, 7}),
                new StubTicketSaver(new IOException("Disk full"))
        );

        PosTicketDeliveryCoordinator.DeliveryOutcome outcome = coordinator.execute(success());

        PosTicketDeliveryCoordinator.TicketSaveFailed failed =
                assertInstanceOf(PosTicketDeliveryCoordinator.TicketSaveFailed.class, outcome);
        assertEquals("TXN-20260413-194637-B9", failed.transactionId());
        assertTrue(
                failed.operatorMessage().contains("TicketSync could not save the ticket file"),
                "Failure message should explain that saving did not succeed"
        );
    }

    private static PosPurchaseCoordinator.PurchaseSuccess success() {
        Sale sale = new Sale(
                501,
                12,
                9,
                new BigDecimal("79.49"),
                LocalDateTime.of(2026, 4, 13, 19, 46, 37),
                "Booth 9"
        );
        return new PosPurchaseCoordinator.PurchaseSuccess(
                PurchaseReceiptDetails.fromSale(sale, List.of("Floor, Row A, Seat 12")),
                sale
        );
    }

    private static final class StubSaleItemsLookup implements PosTicketDeliveryCoordinator.SaleItemsLookup {
        private int requestedSaleId;

        @Override
        public List<SaleItem> getSaleItemsBySaleId(int saleId) {
            requestedSaleId = saleId;
            return List.of(new SaleItem(0, saleId, 301, new BigDecimal("79.49")));
        }
    }

    private static final class StubTicketPdfGenerator implements PosTicketDeliveryCoordinator.TicketPdfGenerator {
        private final byte[] pdfBytes;

        private StubTicketPdfGenerator(byte[] pdfBytes) {
            this.pdfBytes = pdfBytes;
        }

        @Override
        public byte[] generateTicket(Sale sale, List<SaleItem> saleItems) throws TicketGenerationException {
            return pdfBytes;
        }
    }

    private static final class StubTicketSaver implements PosTicketDeliveryCoordinator.TicketSaver {
        private final Path savedPath;
        private final IOException failure;
        private byte[] savedPdfData;

        private StubTicketSaver(Path savedPath) {
            this.savedPath = savedPath;
            this.failure = null;
        }

        private StubTicketSaver(IOException failure) {
            this.savedPath = null;
            this.failure = failure;
        }

        @Override
        public Path saveTicket(Sale sale, byte[] pdfData) throws IOException {
            if (failure != null) {
                throw failure;
            }
            savedPdfData = pdfData;
            return savedPath;
        }
    }
}
