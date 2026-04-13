package com.ticketsync.service;

import com.ticketsync.dao.EventDAO;
import com.ticketsync.dao.SeatDAO;
import com.ticketsync.dao.ZoneDAO;
import com.ticketsync.exception.TicketGenerationException;
import com.ticketsync.model.Event;
import com.ticketsync.model.Sale;
import com.ticketsync.model.SaleItem;
import com.ticketsync.model.Seat;
import com.ticketsync.model.SeatStatus;
import com.ticketsync.model.Zone;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketGeneratorTest {

    private StubEventDAO eventDAO;
    private StubSeatDAO seatDAO;
    private StubZoneDAO zoneDAO;
    private TicketGenerator generator;

    @BeforeEach
    void setUp() {
        eventDAO = new StubEventDAO();
        seatDAO = new StubSeatDAO();
        zoneDAO = new StubZoneDAO();
        generator = new TicketGenerator(eventDAO, seatDAO, zoneDAO, TicketGeneratorTest::noopConnection);
    }

    @Test
    void generateTicket_singleSeatIncludesRequiredContentAndPageSize() throws Exception {
        eventDAO.event = Optional.of(new Event(
                77,
                "Festival d'Été 2026",
                LocalDateTime.of(2026, 6, 30, 19, 30),
                "Café Arena",
                null,
                true,
                1,
                LocalDateTime.of(2026, 4, 1, 9, 0)
        ));
        zoneDAO.zonesByEvent.put(77, List.of(new Zone(10, 77, "Floor", new BigDecimal("79.49"))));
        seatDAO.seatsById.put(301, new Seat(301, 10, "A", "12", SeatStatus.SOLD, 42));

        Sale sale = new Sale(
                42,
                77,
                5,
                new BigDecimal("79.49"),
                LocalDateTime.of(2026, 4, 11, 14, 15, 9),
                "Booth 5"
        );
        SaleItem item = new SaleItem(0, 0, 301, new BigDecimal("79.49"));

        byte[] pdfBytes = generator.generateTicket(sale, List.of(item));

        assertTrue(pdfBytes.length > 0, "PDF bytes should not be empty");
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEquals(1, document.getNumberOfPages());

            PDRectangle expectedSize = TicketGenerator.ticketPageSize();
            PDRectangle actualSize = document.getPage(0).getMediaBox();
            assertEquals(expectedSize.getWidth(), actualSize.getWidth(), 0.1f);
            assertEquals(expectedSize.getHeight(), actualSize.getHeight(), 0.1f);

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Festival d'Été 2026"));
            assertTrue(text.contains("Event Date/Time: 30/06/2026 19:30"));
            assertTrue(text.contains("Venue: Café Arena"));
            assertTrue(text.contains("Zone: Floor"));
            assertTrue(text.contains("Row: A"));
            assertTrue(text.contains("Seat Number: 12"));
            assertTrue(text.contains("Price: €79.49"));
            assertTrue(text.contains("Sold on: 11/04/2026 14:15"));
            assertTrue(text.contains("Booth: Booth 5"));
            assertTrue(text.contains("TXN-20260411-141509-B5"));
        }
    }

    @Test
    void generateTicket_multiSeatSaleCreatesOnePagePerSeat() throws Exception {
        eventDAO.event = Optional.of(new Event(
                88,
                "Spring Gala",
                LocalDateTime.of(2026, 7, 1, 20, 0),
                "Grand Hall",
                null,
                true,
                1,
                LocalDateTime.of(2026, 4, 1, 9, 0)
        ));
        zoneDAO.zonesByEvent.put(88, List.of(
                new Zone(10, 88, "Floor", new BigDecimal("49.50")),
                new Zone(20, 88, "Balcony", new BigDecimal("59.00"))
        ));
        seatDAO.seatsById.put(501, new Seat(501, 10, "A", "1", SeatStatus.SOLD, 81));
        seatDAO.seatsById.put(502, new Seat(502, 20, "B", "7", SeatStatus.SOLD, 81));

        Sale sale = new Sale(
                81,
                88,
                4,
                new BigDecimal("108.50"),
                LocalDateTime.of(2026, 4, 12, 10, 5, 0),
                "Booth 2"
        );

        byte[] pdfBytes = generator.generateTicket(sale, List.of(
                new SaleItem(0, 0, 501, new BigDecimal("49.50")),
                new SaleItem(0, 0, 502, new BigDecimal("59.00"))
        ));

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, document.getNumberOfPages());
            assertTrue(extractPageText(document, 1).contains("Seat Number: 1"));
            assertTrue(extractPageText(document, 1).contains("Price: €49.50"));
            assertTrue(extractPageText(document, 2).contains("Zone: Balcony"));
            assertTrue(extractPageText(document, 2).contains("Seat Number: 7"));
            assertTrue(extractPageText(document, 2).contains("Price: €59.00"));
        }
    }

    @Test
    void generateTicket_preservesEuroSymbolAndAccentedText() throws Exception {
        eventDAO.event = Optional.of(new Event(
                91,
                "Fête Européenne",
                LocalDateTime.of(2026, 8, 14, 18, 45),
                "Café Théâtre Élite",
                null,
                true,
                1,
                LocalDateTime.of(2026, 4, 1, 9, 0)
        ));
        zoneDAO.zonesByEvent.put(91, List.of(new Zone(30, 91, "Première", new BigDecimal("109.95"))));
        seatDAO.seatsById.put(777, new Seat(777, 30, "C", "3", SeatStatus.SOLD, 99));

        byte[] pdfBytes = generator.generateTicket(
                new Sale(99, 91, 7, new BigDecimal("109.95"), LocalDateTime.of(2026, 4, 13, 11, 0, 30), "Booth 9"),
                List.of(new SaleItem(0, 0, 777, new BigDecimal("109.95")))
        );

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Fête Européenne"));
            assertTrue(text.contains("Café Théâtre Élite"));
            assertTrue(text.contains("Zone: Première"));
            assertTrue(text.contains("Price: €109.95"));
        }
    }

    @Test
    void generateTicket_missingSeatThrowsTicketGenerationException() {
        eventDAO.event = Optional.of(new Event(
                55,
                "City Lights",
                LocalDateTime.of(2026, 9, 1, 21, 0),
                "Main Venue",
                null,
                true,
                1,
                LocalDateTime.of(2026, 4, 1, 9, 0)
        ));
        zoneDAO.zonesByEvent.put(55, List.of(new Zone(40, 55, "Floor", new BigDecimal("35.00"))));

        TicketGenerationException exception = assertThrows(
                TicketGenerationException.class,
                () -> generator.generateTicket(
                        new Sale(12, 55, 3, new BigDecimal("35.00"), LocalDateTime.of(2026, 4, 13, 15, 30, 0), "Booth 1"),
                        List.of(new SaleItem(0, 0, 999, new BigDecimal("35.00")))
                )
        );

        assertTrue(exception.getMessage().contains("Seat not found"));
    }

    private static String extractPageText(PDDocument document, int pageNumber) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(document);
    }

    private static Connection noopConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == void.class) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException("Unsupported primitive type: " + returnType);
    }

    private static final class StubEventDAO implements EventDAO {
        private Optional<Event> event = Optional.empty();

        @Override
        public Optional<Event> findById(Connection conn, int eventId) {
            return event.filter(candidate -> candidate.getEventId() == eventId);
        }

        @Override
        public List<Event> findAll(Connection conn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Event> findActive(Connection conn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(Connection conn, Event event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Connection conn, Event event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Connection conn, int eventId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubSeatDAO implements SeatDAO {
        private final Map<Integer, Seat> seatsById = new HashMap<>();

        @Override
        public Optional<Seat> findById(Connection conn, int seatId) {
            return Optional.ofNullable(seatsById.get(seatId));
        }

        @Override
        public List<Seat> findByZoneId(Connection conn, int zoneId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Seat> findByEventId(Connection conn, int eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Seat> selectForUpdate(Connection conn, List<Integer> seatIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateStatus(Connection conn, List<Integer> seatIds, SeatStatus status, Integer saleId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(Connection conn, Seat seat) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Connection conn, int seatId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubZoneDAO implements ZoneDAO {
        private final Map<Integer, List<Zone>> zonesByEvent = new HashMap<>();

        @Override
        public Optional<Zone> findById(Connection conn, int zoneId) {
            return zonesByEvent.values().stream()
                    .flatMap(List::stream)
                    .filter(zone -> zone.getZoneId() == zoneId)
                    .findFirst();
        }

        @Override
        public List<Zone> findByEventId(Connection conn, int eventId) {
            return zonesByEvent.getOrDefault(eventId, List.of());
        }

        @Override
        public int insert(Connection conn, Zone zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Connection conn, Zone zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Connection conn, int zoneId) {
            throw new UnsupportedOperationException();
        }
    }
}
