package com.baseerah.rescue;

import java.math.BigDecimal;

/**
 * One bridge option offered to cover a predicted deficit (FR-07, DESIGN.md §5.4). Two are produced per
 * assessment — a {@link RescueOptionType#MURABAHA} and a {@link RescueOptionType#LIQUIDATE} — sized from
 * the client's telemetry, never from prototype constants (ORCHESTRATION Global Rules).
 *
 * <p>An internal domain type: it is the unit {@code RescueService.confirm} accepts and the shape the Step
 * 4.2 controller will map to/from a wire DTO. It never crosses the controller boundary directly.
 *
 * @param type   which bridge this is (drives the persisted {@code option_chosen} and the recovery curve)
 * @param label  short human-readable name for the option (e.g. "Murabaha micro-finance")
 * @param amount the SAR amount the bridge provides — sized to cover the shortfall
 * @param term   repayment term in months for {@code MURABAHA}; {@code null} for {@code LIQUIDATE} (no term)
 * @param detail a one-line explanation of what the option does, for the UI
 */
public record RescueOption(
        RescueOptionType type,
        String label,
        BigDecimal amount,
        Integer term,
        String detail) {
}
