package com.baseerah.domain.stress;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The immutable domain snapshot of a client's Financial Stress Score (DESIGN.md §5.1) — the value the
 * application layer returns to the web layer, decoupled from the {@code StressScoreJpaEntity} that persists
 * it. The persistence mapper builds it from the stored row; the web mapper projects it to the API response
 * (resolving the gauge colour), so the JPA entity never reaches {@code api}.
 *
 * <p>{@code score} is the integer 0–100 index and {@code zone} its band; the three sub-scores are on the
 * same 0–100 healthiness scale (higher = healthier) as persisted, for the radar chart.
 *
 * @param score             integer 0–100 stress index (higher = healthier)
 * @param zone              health band for {@code score} (DESIGN.md §5.1)
 * @param spendingVelocity  spending-velocity sub-score, 0–100
 * @param incomeConsistency income-consistency sub-score, 0–100
 * @param liabilityRatio    liability-ratio sub-score, 0–100
 * @param asOfDate          the day this snapshot was computed for
 */
public record StressScore(
        int score,
        Zone zone,
        BigDecimal spendingVelocity,
        BigDecimal incomeConsistency,
        BigDecimal liabilityRatio,
        LocalDate asOfDate) {
}
