package com.baseerah.domain.stress;

/**
 * Health band for a Financial Stress Score (DESIGN.md §5.1). Higher score = healthier, matching the
 * consumer gauge (green = optimal). Thresholds are the prototype's: {@code >=70 OPTIMAL},
 * {@code 40–69 WARNING}, {@code <40 CRITICAL}. Persisted as {@code name()} in {@code stress_scores.zone}.
 */
public enum Zone {
    CRITICAL,
    WARNING,
    OPTIMAL;

    /** Maps a 0–100 score to its zone using the §5.1 thresholds. */
    public static Zone forScore(int score) {
        if (score >= 70) {
            return OPTIMAL;
        }
        if (score >= 40) {
            return WARNING;
        }
        return CRITICAL;
    }
}
