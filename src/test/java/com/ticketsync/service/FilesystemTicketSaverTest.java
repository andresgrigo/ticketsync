package com.ticketsync.service;

import com.ticketsync.model.Sale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesystemTicketSaverTest {

    @TempDir
    Path tempDir;

    @Test
    void saveTicket_createsDatedDirectoryAndPreservesPdfBytes() throws Exception {
        Path ticketsRoot = tempDir.resolve("tickets-root");
        FilesystemTicketSaver saver = new FilesystemTicketSaver(ticketsRoot);
        Sale sale = saleAt(LocalDateTime.of(2026, 4, 13, 19, 46, 37), "Booth 9");
        byte[] pdfBytes = createPdfBytes();

        Path savedPath = saver.saveTicket(sale, pdfBytes);

        Path expectedPath = ticketsRoot.toAbsolutePath().normalize()
                .resolve("2026-04-13")
                .resolve("TXN-20260413-194637-B9.pdf");
        assertEquals(expectedPath, savedPath);
        assertTrue(Files.isDirectory(savedPath.getParent()));
        assertArrayEquals(pdfBytes, Files.readAllBytes(savedPath));
        try (PDDocument document = Loader.loadPDF(Files.readAllBytes(savedPath))) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void saveTicket_throwsWhenTargetPdfAlreadyExists() throws Exception {
        Path ticketsRoot = tempDir.resolve("tickets-root");
        FilesystemTicketSaver saver = new FilesystemTicketSaver(ticketsRoot);
        Sale sale = saleAt(LocalDateTime.of(2026, 4, 13, 19, 46, 37), "Booth 9");
        byte[] pdfBytes = createPdfBytes();

        saver.saveTicket(sale, pdfBytes);

        assertThrows(FileAlreadyExistsException.class, () -> saver.saveTicket(sale, pdfBytes));
    }

    private static Sale saleAt(LocalDateTime timestamp, String boothId) {
        return new Sale(77, 12, 9, java.math.BigDecimal.TEN, timestamp, boothId);
    }

    private static byte[] createPdfBytes() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
