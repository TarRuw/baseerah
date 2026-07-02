package com.baseerah.transaction.dto;

import com.baseerah.transaction.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Client-facing view of a single booked transaction (DESIGN.md §6,
 * {@code GET /api/v1/clients/{id}/transactions}).
 *
 * <p>{@code category} carries the <em>raw</em> feed string verbatim (lossless); the typed
 * {@code Category} view is an internal scoring/analytics concern, not part of this projection.
 * {@code amount} and {@code closingBalance} are {@code numeric(14,2)} — the Flutter layer formats them.
 *
 * @param id                 canonical UUID primary key
 * @param direction          credit/debit indicator
 * @param amount             transaction amount
 * @param currency           ISO currency code
 * @param rawDescription     original feed description
 * @param descriptionCleansed normalised, display-friendly description
 * @param category           raw feed category string
 * @param categoryConfidence categorisation confidence (0–1), when supplied by the feed
 * @param bookingDate        when the transaction was booked
 * @param closingBalance     account balance immediately after this transaction
 */
public record TransactionDto(
        UUID id,
        Direction direction,
        BigDecimal amount,
        String currency,
        String rawDescription,
        String descriptionCleansed,
        String category,
        BigDecimal categoryConfidence,
        Instant bookingDate,
        BigDecimal closingBalance) {
}
