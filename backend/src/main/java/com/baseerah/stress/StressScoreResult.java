package com.baseerah.stress;

/**
 * Immutable output of {@link StressScoreCalculator} for one client over one window (DESIGN.md §5.1).
 *
 * <p>{@code score} is the integer 0–100 healthiness index and {@code zone} its band. The three
 * {@code *SubScore} values are the normalised components on a {@code [0,1]} scale (higher = healthier);
 * {@link StressScoreSnapshotWriter} scales them to 0–100 for persistence and the radar chart.
 */
public record StressScoreResult(
        int score,
        Zone zone,
        double velocitySubScore,
        double consistencySubScore,
        double liabilitySubScore) {
}
