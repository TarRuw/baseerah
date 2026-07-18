package com.baseerah.domain.rescue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Domain result of {@code RescueService.assess} for one client (FR-06, DESIGN.md §5.4) — the "shortfall +
 * options" assessment. Reports whether a cash-flow deficit is predicted, when, how far off it is, whether
 * that is inside the 15-day alert lead window, the magnitude to bridge, and the two options offered. A pure
 * domain value (step 10.6); {@code api/rescue/RescueWebMapper} maps it to the wire {@code RescueResponse}.
 *
 * <p><strong>Faithful-engine note.</strong> The headline persona {@code client_003_freelancer} is
 * engineered (db-seed/generate_personas.py) as an irregular-income earner whose steady daily burn no longer
 * fits a thin buffer, so the faithful base forecast crosses zero within the home horizon — a real, computed
 * deficit with a short lead time. Every field here is derived from that projection over the seeded telemetry,
 * never a fabricated constant (Global Rules).
 *
 * @param hasDeficit         whether a deficit is projected within the assessment horizon
 * @param deficitDate        the first day the balance is projected negative, or {@code null} when none
 * @param deficitInDays      whole days from the projection's start day to {@code deficitDate} ({@code 0} when none)
 * @param alertRaised        whether {@code deficitInDays} is within the alert lead window — the FR-06
 *                           "raise the alert 15 days before" trigger
 * @param predictedShortfall the SAR magnitude to bridge over the deficit (positive; {@code 0} when no deficit)
 * @param options            the bridge options offered — two when a deficit exists, empty otherwise
 */
public record RescueAssessment(
        boolean hasDeficit,
        LocalDate deficitDate,
        int deficitInDays,
        boolean alertRaised,
        BigDecimal predictedShortfall,
        List<RescueOption> options) {

    /** A no-deficit assessment: nothing to alert on, no shortfall, no options (a healthy persona). */
    public static RescueAssessment noDeficit() {
        return new RescueAssessment(false, null, 0, false, BigDecimal.ZERO, List.of());
    }
}
