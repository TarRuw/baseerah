package com.baseerah.domain.rescue;

import java.math.BigDecimal;

/**
 * One bridge option offered to cover a predicted deficit (FR-07, DESIGN.md §5.4). Two are produced per
 * assessment — a {@link RescueOptionType#MURABAHA} and a {@link RescueOptionType#LIQUIDATE} — sized from
 * the client's telemetry, never from prototype constants (ORCHESTRATION Global Rules).
 *
 * <p>A pure domain value (step 10.6): it carries only the <em>typed</em> facts of the offer — which bridge,
 * the SAR amount, and the repayment term. The human-facing {@code label}/{@code detail} are a presentation
 * concern resolved for the request locale in {@code api/rescue/RescueWebMapper} (mirroring how the loan
 * verdict text and stress gauge colour are resolved in their web mappers), so this record stays framework-
 * and locale-free.
 *
 * @param type   which bridge this is (drives the persisted {@code option_chosen} and the recovery curve)
 * @param amount the SAR amount the bridge provides — sized to cover the shortfall
 * @param term   repayment term in months for {@code MURABAHA}; {@code null} for {@code LIQUIDATE} (no term)
 */
public record RescueOption(
        RescueOptionType type,
        BigDecimal amount,
        Integer term) {
}
