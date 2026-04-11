package com.ticketsync.service;

import com.ticketsync.model.Sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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

    public PurchaseReceiptDetails {
        transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
        timestampText = Objects.requireNonNull(timestampText, "timestampText must not be null");
        seatLines = List.copyOf(Objects.requireNonNull(seatLines, "seatLines must not be null"));
        totalPriceText = Objects.requireNonNull(totalPriceText, "totalPriceText must not be null");
        boothId = normalizeBoothId(boothId);
    }

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

    public static String formatTransactionId(Sale sale) {
        Objects.requireNonNull(sale, "sale must not be null");
        LocalDateTime timestamp = Objects.requireNonNull(sale.getSaleTimestamp(), "saleTimestamp must not be null");
        return "TXN-" + timestamp.format(TRANSACTION_ID_TIMESTAMP) + "-" + boothSuffix(sale.getBoothId());
    }

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
