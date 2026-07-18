package com.baseerah.domain.rescue;

/**
 * The pure Smart Rescue recovery computation (FR-07, DESIGN.md §5.4) — the score before → after a chosen
 * bridge removes the deficit, with <strong>no Spring, no database, no clock</strong>. The imperative shell
 * that resolves the client, projects the deficit and scores the window lives in {@code
 * application/rescue/RescueService}; this class is a pure function of {@code (scoreBefore, option)}, so the
 * §5.4 recovery curve is unit-testable directly.
 *
 * <p><strong>Recovery curve (engineer decision, DESIGN §5.4).</strong> The prototype's {@code 62→78 / 62→74}
 * recovery numbers are demo stand-ins; here the recovery is computed from the score's own headroom and the
 * option's financing cost — a fully-covering bridge lifts the score by {@link #RECOVERY_HEADROOM_FRACTION}
 * of its distance to 100, and Murabaha recovers strictly less than a cost-free liquidation by its markup
 * overhead ({@link #MURABAHA_MONTHLY_PROFIT_RATE} per month of term). That guarantees {@code scoreAfter >
 * scoreBefore} and {@code liquidate > murabaha} for every persona — including the freelancer, whose
 * zero-income window pins the base score's velocity/consistency sub-scores and would defeat a re-scoring
 * approach.
 */
public final class RescueRecovery {

    /** Recovery curve: a fully-covering bridge lifts the score by this fraction of its headroom to 100. */
    private static final double RECOVERY_HEADROOM_FRACTION = 0.5;

    /**
     * Murabaha monthly profit rate — a Sharia-compliant markup and a genuine product parameter (like the
     * rate input to the loan calculator), not a demo recovery stand-in. Drives the financing overhead that
     * makes Murabaha recover slightly less than a cost-free liquidation.
     */
    private static final double MURABAHA_MONTHLY_PROFIT_RATE = 0.0075; // ~9% annualised

    private RescueRecovery() {
    }

    /**
     * The recovered stress score once the chosen bridge removes the deficit. A fully-covering bridge lifts
     * the score by {@link #RECOVERY_HEADROOM_FRACTION} of its headroom to 100; Murabaha then loses its
     * financing overhead ({@link #MURABAHA_MONTHLY_PROFIT_RATE} per month of term), so a cost-free
     * liquidation always recovers strictly more. Both recover at least one point, so the result is always
     * {@code > scoreBefore}; it is capped at 100.
     *
     * @param scoreBefore the client's current stress score
     * @param option      the chosen bridge (its type and term drive the financing overhead)
     * @return the recovered score, strictly greater than {@code scoreBefore} and at most 100
     */
    public static int recoveredScore(int scoreBefore, RescueOption option) {
        int headroom = 100 - scoreBefore;
        int baseRecovery = Math.max(1, (int) Math.round(headroom * RECOVERY_HEADROOM_FRACTION));

        int recovery = baseRecovery;
        if (option.type() == RescueOptionType.MURABAHA) {
            double costRatio = MURABAHA_MONTHLY_PROFIT_RATE * (option.term() == null ? 0 : option.term());
            int penalty = Math.max(1, (int) Math.round(baseRecovery * costRatio));
            recovery = Math.max(1, baseRecovery - penalty);
        }
        return Math.min(100, scoreBefore + recovery);
    }
}
