package com.ticketsync.service;

import com.ticketsync.model.Sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Objeto de valor inmutable que contiene todos los campos necesarios para renderizar un recibo de compra.
 *
 * <p>Las instancias se crean vía {@link #fromSale(Sale, List)} o directamente a través del
 * constructor canónico del record. El constructor compacto valida y normaliza
 * cada componente para que cada {@code PurchaseReceiptDetails} esté garantizado de ser
 * no-null y listo para mostrar.
 *
 * @param transactionId  identificador de transacción formateado (p.ej. {@code TXN-20240101-120000-BBOOTH})
 * @param timestampText  timestamp de venta legible por humanos (p.ej. {@code January 1, 2024 12:00:00})
 * @param seatLines      lista no modificable de descripciones de asientos incluidos en el recibo
 * @param totalPriceText precio total formateado (p.ej. {@code EUR15.00})
 * @param boothId        identificador de cabina normalizado; nunca {@code null}
 */
public record PurchaseReceiptDetails(
        String transactionId,
        String timestampText,
        List<String> seatLines,
        String totalPriceText,
        String boothId
) {

    private static final DateTimeFormatter TRANSACTION_ID_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter HUMAN_TIMESTAMP =
            DateTimeFormatter.ofPattern("MMMM d, yyyy HH:mm:ss", Locale.ENGLISH);

    /**
     * Constructor compacto: valida campos requeridos y normaliza {@code boothId}.
     *
     * @throws NullPointerException si algún campo requerido es {@code null}
     */
    public PurchaseReceiptDetails {
        transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        timestampText = Objects.requireNonNull(timestampText, "timestampText must not be null");
        seatLines = List.copyOf(Objects.requireNonNull(seatLines, "seatLines must not be null"));
        totalPriceText = Objects.requireNonNull(totalPriceText, "totalPriceText must not be null");
        boothId = normalizeBoothId(boothId);
    }

    /**
     * Construye un {@code PurchaseReceiptDetails} a partir de una venta completada.
     *
     * @param sale      la venta completada; no debe ser {@code null}
     * @param seatLines descripciones de asientos legibles por humanos para incluir en el recibo
     * @return un objeto de detalles de recibo completamente poblado; nunca {@code null}
     */
    public static PurchaseReceiptDetails fromSale(Sale sale, List<String> seatLines) {
        Objects.requireNonNull(sale, "sale must not be null");
        return new PurchaseReceiptDetails(
                formatTransactionId(sale),
                formatTimestamp(sale.getSaleTimestamp()),
                seatLines,
                formatCurrency(sale.getTotalAmount()),
                sale.getBoothId()
        );
    }

    /**
     * Formatea la cadena de ID de transacción para la venta dada.
     *
     * <p>Formato: {@code TXN-<yyyyMMdd-HHmmss>-B<sufijoCabina>}.
     *
     * @param sale la venta completada; no debe ser {@code null}
     * @return ID de transacción formateado; nunca {@code null}
     */
    public static String formatTransactionId(Sale sale) {
        Objects.requireNonNull(sale, "sale must not be null");
        LocalDateTime timestamp = Objects.requireNonNull(sale.getSaleTimestamp(), "saleTimestamp must not be null");
        return "TXN-" + timestamp.format(TRANSACTION_ID_TIMESTAMP) + "-" + boothSuffix(sale.getBoothId());
    }

    /**
     * Formatea un {@link LocalDateTime} como un timestamp de recibo legible por humanos.
     *
     * @param timestamp el timestamp a formatear; no debe ser {@code null}
     * @return cadena formateada como {@code January 1, 2024 12:00:00}
     */
    public static String formatTimestamp(LocalDateTime timestamp) {
        return Objects.requireNonNull(timestamp, "timestamp must not be null").format(HUMAN_TIMESTAMP);
    }

    private static String formatCurrency(BigDecimal amount) {
        BigDecimal normalized = Objects.requireNonNull(amount, "amount must not be null")
                .setScale(2, RoundingMode.HALF_UP);
        return "EUR" + normalized.toPlainString();
    }

    private static String boothSuffix(String boothId) {
        if (boothId == null || boothId.isBlank()) {
            return "BUNKNOWN";
        }
        String stripped = boothId.strip().replaceFirst("(?i)^Booth\\s*", "").replaceAll("[^A-Za-z0-9]", "");
        if (stripped.isBlank()) {
            return "BUNKNOWN";
        }
        return "B" + stripped.toUpperCase(Locale.ENGLISH);
    }

    private static String normalizeBoothId(String boothId) {
        if (boothId == null || boothId.isBlank()) {
            return "Unknown Booth";
        }
        return boothId.strip();
    }
}
