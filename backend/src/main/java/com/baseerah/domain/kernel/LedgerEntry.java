package com.baseerah.domain.kernel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The domain view of a single booked transaction that the rule-bearing algorithms consume (DESIGN.md §5).
 * A pure, framework-free projection of the {@code Transaction} JPA entity: an application service loads the
 * entity via its repository and maps it to a {@link LedgerEntry} (resolving the raw feed category to a
 * typed {@link Category}) <em>before</em> handing the window to a domain calculator, so the domain never
 * imports {@code jakarta.persistence}.
 *
 * @param accountId          the account this entry was booked on. A client may hold several accounts, and
 *                           {@link #closingBalance} is scoped to one of them — so any algorithm deriving a
 *                           client-wide balance must take the latest entry <em>per account</em> and sum
 *                           those, never the single latest entry of the merged window.
 * @param direction          credit/debit indicator
 * @param amount             signed-agnostic amount as booked (algorithms take {@code abs()} as needed)
 * @param category           typed category, resolved from the raw feed string (never {@code null})
 * @param descriptionCleansed cleansed description used to group recurring obligations ({@code null} allowed)
 * @param bookingDate        instant the entry was booked
 * @param closingBalance     closing balance of {@link #accountId} after the entry ({@code null} when unknown)
 */
public record LedgerEntry(
        UUID accountId,
        Direction direction,
        BigDecimal amount,
        Category category,
        String descriptionCleansed,
        Instant bookingDate,
        BigDecimal closingBalance) {
}
