package com.baseerah.stress;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * API view of a client's latest Financial Stress Score (DESIGN.md §5.1, §6). Serialized inside the shared
 * success envelope by {@code GET /api/v1/clients/{id}/stress-score} and consumed by the Step 2.3
 * Home/Radar gauge. The JPA {@link StressScore} entity never crosses the controller boundary — this
 * immutable projection does (Global Rules).
 *
 * <p>{@code score} is the integer 0–100 index and {@code zone} its band; {@code color} is the zone's gauge
 * hex from DESIGN.md §8, resolved here because the colour belongs to the API response — the values are kept
 * identical to the Step 2.3 Flutter theme tokens so the served colour and the rendered zone never disagree.
 * The three sub-scores are on the same 0–100 healthiness scale (higher = healthier) for the radar chart.
 *
 * @param score            integer 0–100 stress index (higher = healthier)
 * @param zone             health band for {@code score} (DESIGN.md §5.1)
 * @param color            the zone's gauge hex (DESIGN.md §8)
 * @param spendingVelocity spending-velocity sub-score, 0–100
 * @param incomeConsistency income-consistency sub-score, 0–100
 * @param liabilityRatio   liability-ratio sub-score, 0–100
 * @param asOfDate         the day this snapshot was computed for
 */
public record StressScoreResponse(
        int score,
        Zone zone,
        String color,
        BigDecimal spendingVelocity,
        BigDecimal incomeConsistency,
        BigDecimal liabilityRatio,
        LocalDate asOfDate) {

    /** Gauge zone colours (DESIGN.md §8) — must stay identical to the Step 2.3 Flutter theme tokens. */
    private static final String OPTIMAL_HEX = "#1D9E63";
    private static final String WARNING_HEX = "#E5A63A";
    private static final String CRITICAL_HEX = "#E0574F";

    /** Project a persisted snapshot to its API view, resolving the zone's gauge colour. */
    public static StressScoreResponse from(StressScore snapshot) {
        return new StressScoreResponse(
                snapshot.getScore(),
                snapshot.getZone(),
                colorFor(snapshot.getZone()),
                snapshot.getSpendingVelocity(),
                snapshot.getIncomeConsistency(),
                snapshot.getLiabilityRatio(),
                snapshot.getAsOfDate());
    }

    /** Map a zone to its gauge hex (DESIGN.md §8). */
    private static String colorFor(Zone zone) {
        return switch (zone) {
            case OPTIMAL -> OPTIMAL_HEX;
            case WARNING -> WARNING_HEX;
            case CRITICAL -> CRITICAL_HEX;
        };
    }
}
