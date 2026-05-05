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
 * Immutable value object holding all fields needed to render a purchase receipt.
 *
 * <p>Instances are created via {@link #fromSale(Sale, List)} or directly through
 * the canonical record constructor. The compact constructor validates and normalises
 * each component so that every {@code PurchaseReceiptDetails} is guaranteed to be
 * non-null and display-ready.
 *
 * @param transactionId  formatted transaction identifier (e.g. {@code TXN-20240101-120000-BBOOTH})
 * @param timestampText  human-readable sale timestamp (e.g. {@code January 1, 2024 12:00:00})
 * @param seatLines      unmodifiable list of seat descriptions included in the receipt
 * @param totalPriceText formatted total price (e.g. {@code EUR15.00})
 * @param boothId        normalised booth identifier; never {@code null}
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
     * Compact constructor: validates required fields and normalises {@code boothId}.
     *
     * @throws NullPointerException if any required field is {@code null}
     */
    public PurchaseReceiptDetails {
        transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        timestampText = Objects.requireNonNull(timestampText, "timestampText must not be null");
        seatLines = List.copyOf(Objects.requireNonNull(seatLines, "seatLines must not be null"));
        totalPriceText = Objects.requireNonNull(totalPriceText, "totalPriceText must not be null");
        boothId = normalizeBoothId(boothId);
    }

    /**
     * Builds a {@code PurchaseReceiptDetails} from a completed sale.
     *
     * @param sale      the completed sale; must not be {@code null}
     * @param seatLines human-readable seat descriptions to include on the receipt
     * @return a fully populated receipt details object; never {@code null}
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
     * Formats the transaction ID string for the given sale.
     *
     * <p>Format: {@code TXN-<yyyyMMdd-HHmmss>-B<boothSuffix>}.
     *
     * @param sale the completed sale; must not be {@code null}
     * @return formatted transaction ID; never {@code null}
     */
    public static String formatTransactionId(Sale sale) {
        Objects.requireNonNull(sale, "sale must not be null");
        LocalDateTime timestamp = Objects.requireNonNull(sale.getSaleTimestamp(), "saleTimestamp must not be null");
        return "TXN-" + timestamp.format(TRANSACTION_ID_TIMESTAMP) + "-" + boothSuffix(sale.getBoothId());
    }

    /**
     * Formats a {@link LocalDateTime} as a human-readable receipt timestamp.
     *
     * @param timestamp the timestamp to format; must not be {@code null}
     * @return formatted string such as {@code January 1, 2024 12:00:00}
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
