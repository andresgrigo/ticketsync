package com.ticketsync.service;

import com.ticketsync.dao.EventDAO;
import com.ticketsync.dao.EventDAOImpl;
import com.ticketsync.dao.SeatDAO;
import com.ticketsync.dao.SeatDAOImpl;
import com.ticketsync.dao.ZoneDAO;
import com.ticketsync.dao.ZoneDAOImpl;
import com.ticketsync.exception.TicketGenerationException;
import com.ticketsync.model.Event;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.model.Seat;
import com.ticketsync.model.Zone;
import com.ticketsync.util.DatabaseConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Genera PDFs de boletos imprimibles como bytes en memoria para impresión posterior o guardado alternativo.
 */
public class TicketGenerator {

    private static final Logger LOGGER = LogManager.getLogger(TicketGenerator.class);

    private static final float POINTS_PER_MILLIMETER = 72f / 25.4f;
    private static final float PAGE_WIDTH_MM = 80f;
    private static final float PAGE_HEIGHT_MM = 200f;
    private static final float PAGE_MARGIN = 18f;
    private static final float HEADER_FONT_SIZE = 18f;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float FOOTER_FONT_SIZE = 10f;
    private static final float SECTION_GAP = 8f;
    private static final String PDFBOX_UNICODE_FONT_RESOURCE =
            "org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private static final DateTimeFormatter EVENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter SOLD_ON_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH);

    private final EventDAO eventDAO;
    private final SeatDAO seatDAO;
    private final ZoneDAO zoneDAO;
    private final ConnectionFactory connFactory;

    /**
     * Crea un nuevo TicketGenerator usando DAO de producción y conexión de base de datos.
     */
    public TicketGenerator() {
        this(new EventDAOImpl(), new SeatDAOImpl(), new ZoneDAOImpl(), DatabaseConfig::getConnection);
    }

    TicketGenerator(EventDAO eventDAO, SeatDAO seatDAO, ZoneDAO zoneDAO, ConnectionFactory connFactory) {
        this.eventDAO = Objects.requireNonNull(eventDAO, "eventDAO must not be null");
        this.seatDAO = Objects.requireNonNull(seatDAO, "seatDAO must not be null");
        this.zoneDAO = Objects.requireNonNull(zoneDAO, "zoneDAO must not be null");
        this.connFactory = Objects.requireNonNull(connFactory, "connFactory must not be null");
    }

    /**
     * Genera un PDF de boleto como bytes en memoria para la venta dada y sus ítems de línea.
     *
     * @param sale  el registro de venta completada; no debe ser null
     * @param items la lista de ítems de asiento incluidos en la venta; no debe ser null ni estar vacía
     * @return bytes PDF crudos listos para imprimir o persistencia en archivo
     * @throws TicketGenerationException si los datos no pueden cargarse o el PDF no puede renderizarse
     */
    public byte[] generateTicket(Sale sale, List<SaleItem> items) throws TicketGenerationException {
        Sale validatedSale = validateSale(sale);
        List<SaleItem> validatedItems = validateItems(items);

        try (Connection conn = connFactory.get()) {
            List<TicketPageData> pageData = loadTicketPageData(conn, validatedSale, validatedItems);
            return renderPdf(pageData);
        } catch (SQLException e) {
            LOGGER.error("Failed to load ticket data for sale '{}'", validatedSale.getSaleId(), e);
            throw new TicketGenerationException("Failed to load ticket data for PDF generation", e);
        } catch (IOException e) {
            LOGGER.error("Failed to render ticket PDF for sale '{}'", validatedSale.getSaleId(), e);
            throw new TicketGenerationException("Failed to render ticket PDF", e);
        }
    }

    static float millimetersToPoints(float millimeters) {
        return millimeters * POINTS_PER_MILLIMETER;
    }

    static PDRectangle ticketPageSize() {
        return new PDRectangle(millimetersToPoints(PAGE_WIDTH_MM), millimetersToPoints(PAGE_HEIGHT_MM));
    }

    private List<TicketPageData> loadTicketPageData(Connection conn, Sale sale, List<SaleItem> items)
            throws SQLException, TicketGenerationException {
        Event event = eventDAO.findById(conn, sale.getEventId())
                .orElseThrow(() -> new TicketGenerationException("Event not found: " + sale.getEventId()));

        Map<Integer, Zone> zonesById = new LinkedHashMap<>();
        for (Zone zone : zoneDAO.findByEventId(conn, sale.getEventId())) {
            zonesById.put(zone.getZoneId(), zone);
        }

        String transactionId = PurchaseReceiptDetails.formatTransactionId(sale);
        String soldOn = formatSoldOn(sale.getSaleTimestamp());
        String boothLine = "Booth: " + normalizeBoothId(sale.getBoothId());
        List<TicketPageData> pages = new ArrayList<>();

        for (SaleItem item : items) {
            Seat seat = seatDAO.findById(conn, item.getSeatId())
                    .orElseThrow(() -> new TicketGenerationException("Seat not found: " + item.getSeatId()));
            Zone zone = zonesById.get(seat.getZoneId());
            if (zone == null) {
                throw new TicketGenerationException("Zone not found for seat: " + seat.getSeatId());
            }

            pages.add(new TicketPageData(
                    event.getName(),
                    formatEventDate(event.getEventDate()),
                    normalizeNullableText(event.getVenue()),
                    zone.getName(),
                    normalizeNullableText(seat.getRowNumber()),
                    normalizeNullableText(seat.getSeatNumber()),
                    formatPrice(item.getPricePaid()),
                    soldOn,
                    boothLine,
                    transactionId
            ));
        }

        return pages;
    }

    private byte[] renderPdf(List<TicketPageData> pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont ticketFont = loadUnicodeFont(document);
            PDFont monospaceFont = new PDType1Font(Standard14Fonts.FontName.COURIER);

            for (TicketPageData pageData : pages) {
                PDPage page = new PDPage(ticketPageSize());
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    renderPage(contentStream, ticketFont, monospaceFont, pageData, page.getMediaBox());
                }
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private PDFont loadUnicodeFont(PDDocument document) throws IOException {
        try (InputStream fontStream = TicketGenerator.class.getClassLoader()
                .getResourceAsStream(PDFBOX_UNICODE_FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new IOException("Missing PDF font resource: " + PDFBOX_UNICODE_FONT_RESOURCE);
            }
            return PDType0Font.load(document, fontStream, true);
        }
    }

    private void renderPage(PDPageContentStream contentStream, PDFont ticketFont, PDFont monospaceFont,
            TicketPageData pageData, PDRectangle pageSize) throws IOException {
        float maxWidth = pageSize.getWidth() - (2 * PAGE_MARGIN);
        float y = pageSize.getHeight() - PAGE_MARGIN;

        y = writeHeader(contentStream, ticketFont, pageData.eventName(), PAGE_MARGIN, y, maxWidth);
        y -= SECTION_GAP;
        y = writeBodyLines(contentStream, ticketFont, pageData.bodyLines(), PAGE_MARGIN, y, maxWidth);
        y -= SECTION_GAP;
        drawSeparator(contentStream, PAGE_MARGIN, y, pageSize.getWidth() - PAGE_MARGIN);
        y -= SECTION_GAP;
        writeFooter(contentStream, monospaceFont, pageData.transactionId(), PAGE_MARGIN, y);
    }

    private float writeHeader(PDPageContentStream contentStream, PDFont font, String text, float x, float y,
            float maxWidth) throws IOException {
        float currentY = y;
        contentStream.setRenderingMode(RenderingMode.FILL_STROKE);
        contentStream.setLineWidth(0.5f);
        for (String line : wrapText(text, font, HEADER_FONT_SIZE, maxWidth)) {
            if (currentY < PAGE_MARGIN) break;
            writeText(contentStream, font, HEADER_FONT_SIZE, x, currentY, line);
            currentY -= HEADER_FONT_SIZE + 4f;
        }
        contentStream.setLineWidth(1f);
        contentStream.setRenderingMode(RenderingMode.FILL);
        return currentY;
    }

    private float writeBodyLines(PDPageContentStream contentStream, PDFont font, List<String> lines, float x, float y,
            float maxWidth) throws IOException {
        float currentY = y;
        outer:
        for (String line : lines) {
            for (String wrappedLine : wrapText(line, font, BODY_FONT_SIZE, maxWidth)) {
                if (currentY < PAGE_MARGIN) break outer;
                writeText(contentStream, font, BODY_FONT_SIZE, x, currentY, wrappedLine);
                currentY -= BODY_FONT_SIZE + 5f;
            }
        }
        return currentY;
    }

    private void writeFooter(PDPageContentStream contentStream, PDFont font, String transactionId, float x, float y)
            throws IOException {
        writeText(contentStream, font, FOOTER_FONT_SIZE, x, y, transactionId);
    }

    private void writeText(PDPageContentStream contentStream, PDFont font, float fontSize, float x, float y,
            String text) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }

    private void drawSeparator(PDPageContentStream contentStream, float startX, float y, float endX)
            throws IOException {
        contentStream.moveTo(startX, y);
        contentStream.lineTo(endX, y);
        contentStream.stroke();
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (text == null || text.isBlank()) {
            return List.of("");
        }

        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                if (textWidth(font, fontSize, word) > maxWidth) {
                    List<String> chunks = splitTokenToWidth(word, font, fontSize, maxWidth);
                    for (int i = 0; i < chunks.size() - 1; i++) {
                        lines.add(chunks.get(i));
                    }
                    currentLine.append(chunks.get(chunks.size() - 1));
                } else {
                    currentLine.append(word);
                }
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private List<String> splitTokenToWidth(String token, PDFont font, float fontSize, float maxWidth)
            throws IOException {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : token.toCharArray()) {
            String candidate = current.toString() + c;
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                current.append(c);
            } else {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                }
                current.setLength(0);
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks.isEmpty() ? List.of(token) : chunks;
    }

    private float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    private Sale validateSale(Sale sale) {
        Objects.requireNonNull(sale, "sale must not be null");
        if (sale.getEventId() <= 0) {
            throw new IllegalArgumentException("sale.eventId must be positive");
        }
        if (sale.getSaleTimestamp() == null) {
            throw new IllegalArgumentException("sale.saleTimestamp must not be null");
        }
        return sale;
    }

    private List<SaleItem> validateItems(List<SaleItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        List<SaleItem> copy = List.copyOf(items);
        for (SaleItem item : copy) {
            Objects.requireNonNull(item, "items must not contain null values");
            if (item.getSeatId() <= 0) {
                throw new IllegalArgumentException("sale item seatId must be positive");
            }
            Objects.requireNonNull(item.getPricePaid(), "sale item pricePaid must not be null");
        }
        return copy;
    }

    private String formatEventDate(LocalDateTime eventDate) throws TicketGenerationException {
        if (eventDate == null) {
            throw new TicketGenerationException("Event date is required for ticket generation");
        }
        return "Event Date/Time: " + eventDate.format(EVENT_DATE_FORMAT);
    }

    private String formatSoldOn(LocalDateTime saleTimestamp) {
        return "Sold on: " + saleTimestamp.format(SOLD_ON_FORMAT);
    }

    private String formatPrice(BigDecimal pricePaid) {
        return "Price: €" + pricePaid.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalizeBoothId(String boothId) {
        if (boothId == null || boothId.isBlank()) {
            return "Unknown Booth";
        }
        return boothId.strip();
    }

    private String normalizeNullableText(String text) {
        return text == null ? "" : text;
    }

    private record TicketPageData(
            String eventName,
            String eventDateTime,
            String venue,
            String zone,
            String row,
            String seatNumber,
            String price,
            String soldOn,
            String booth,
            String transactionId
    ) {
        private List<String> bodyLines() {
            return List.of(
                    eventDateTime,
                    "Venue: " + venue,
                    "Zone: " + zone,
                    "Row: " + row,
                    "Seat Number: " + seatNumber,
                    price,
                    soldOn,
                    booth
            );
        }
    }
}
