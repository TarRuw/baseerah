package com.baseerah.rescue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outcome of {@link RescueService#assess} for one client (FR-06, DESIGN.md §5.4). Reports whether a
 * cash-flow deficit is predicted, when, how far off it is, whether that is inside the 15-day alert lead
 * window, the magnitude to bridge, and the two options offered. An internal domain type — Step 4.2 maps it
 * to a wire DTO.
 *
 * <p><strong>Faithful-engine note (Step 3.1 / 4.1 deviation, user-approved).</strong> On the frozen
 * {@code data-mocks/} the headline persona {@code client_003_freelancer} has a large, slowly-declining
 * buffer, so their base-forecast deficit is real but years out — {@link #alertRaised} is therefore
 * {@code false} for them, not the 15-day-lead demo the step's prose imagines. The chosen approach is a
 * faithful engine with a realigned test (see the Step 4.1 handoff), rather than fabricating a near-term
 * deficit with prototype constants (forbidden by the Global Rules) or editing the read-only mock.
 *
 * @param hasDeficit         whether a deficit is projected within the assessment horizon
 * @param deficitDate        the first day the balance is projected negative, or {@code null} when none
 * @param deficitInDays      whole days from the projection's start day to {@code deficitDate} ({@code 0} when none)
 * @param alertRaised        whether {@code deficitInDays} is within the {@value RescueService#ALERT_LEAD_DAYS}-day
 *                           lead window — the FR-06 "raise the alert 15 days before" trigger
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
    static RescueAssessment noDeficit() {
        return new RescueAssessment(false, null, 0, false, BigDecimal.ZERO, List.of());
    }
}
