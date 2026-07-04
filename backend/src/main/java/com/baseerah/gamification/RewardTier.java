package com.baseerah.gamification;

/**
 * The consumer-facing gamification tier a client reaches as their Akhtar-Points balance grows (DESIGN.md
 * §5.6). Derived purely from the {@link RewardsLedgerEntry} running sum by {@link #forBalance(int)} — the
 * same "derive, don't store" posture as {@link com.baseerah.stress.Zone#forScore(int)} — so the tier is
 * always consistent with the ledger and never drifts.
 *
 * <p><strong>Distinct from the bank-side {@code risk_tier}</strong> on loan applications (DESIGN.md §4.2,
 * Phase 6): that one grades underwriting risk; this one is a loyalty/engagement rank surfaced on the Goals
 * screen. They are deliberately not the same scale and share no mapping. Thresholds below are the
 * gamification product parameters chosen for Baseerah (documented in the Step 5.1 handoff); a claim awards
 * 50–250 points, so a first claim typically lifts a new saver from BRONZE into SILVER.
 */
public enum RewardTier {

    BRONZE,
    SILVER,
    GOLD,
    PLATINUM;

    /** Inclusive lower bound (points) at which SILVER begins. */
    static final int SILVER_FLOOR = 150;
    /** Inclusive lower bound (points) at which GOLD begins. */
    static final int GOLD_FLOOR = 400;
    /** Inclusive lower bound (points) at which PLATINUM begins. */
    static final int PLATINUM_FLOOR = 800;

    /**
     * The tier for a points {@code balance}. A non-positive balance (a brand-new saver, or a net-zero
     * ledger) resolves to {@link #BRONZE}.
     */
    public static RewardTier forBalance(int balance) {
        if (balance >= PLATINUM_FLOOR) {
            return PLATINUM;
        }
        if (balance >= GOLD_FLOOR) {
            return GOLD;
        }
        if (balance >= SILVER_FLOOR) {
            return SILVER;
        }
        return BRONZE;
    }
}
