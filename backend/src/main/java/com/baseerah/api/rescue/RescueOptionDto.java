package com.baseerah.api.rescue;

import com.baseerah.domain.rescue.RescueOptionType;
import java.math.BigDecimal;

/**
 * Wire view of a single bridge option (FR-07, DESIGN.md §5.4), serialized inside {@link RescueResponse} by
 * {@code GET /api/v1/clients/{id}/rescue} and consumed by the Rescue screen's bridge cards. A plain carrier:
 * {@code RescueWebMapper} projects the domain {@code RescueOption} into it, resolving the locale-specific
 * {@code label}/{@code detail} — that domain record never crosses the controller boundary.
 *
 * @param type   which bridge this is ({@code MURABAHA} | {@code LIQUIDATE}) — echoed back on confirm
 * @param label  short human-readable name (e.g. "Murabaha micro-finance")
 * @param amount SAR amount the bridge provides, sized to cover the shortfall
 * @param term   repayment term in months for {@code MURABAHA}; {@code null} for {@code LIQUIDATE}
 * @param detail one-line explanation of the option for the UI
 */
public record RescueOptionDto(
        RescueOptionType type,
        String label,
        BigDecimal amount,
        Integer term,
        String detail) {
}
