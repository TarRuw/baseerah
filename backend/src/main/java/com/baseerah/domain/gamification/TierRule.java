package com.baseerah.domain.gamification;

/**
 * Pure tier-derivation rule for gamified rewards (DESIGN.md §5.6): maps a client's Akhtar-Points balance to
 * the {@link RewardTier} it falls into. The tier is <em>derived</em> from the ledger sum, never stored, so it
 * cannot drift from the balance — the same posture as {@link com.baseerah.domain.stress.Zone#forScore(int)}.
 *
 * <p>Framework-free (no Spring/JPA); a single stateless function, so it is a {@code static} utility rather
 * than an injected bean. The thresholds below are the gamification product parameters chosen for Baseerah
 * (documented in the Step 5.1 handoff).
 */
public final class TierRule {

    /** Inclusive lower bound (points) at which SILVER begins. */
    static final int SILVER_FLOOR = 150;
    /** Inclusive lower bound (points) at which GOLD begins. */
    static final int GOLD_FLOOR = 400;
    /** Inclusive lower bound (points) at which PLATINUM begins. */
    static final int PLATINUM_FLOOR = 800;

    private TierRule() {
    }

    /**
     * The tier for a points {@code balance}. A non-positive balance (a brand-new saver, or a net-zero
     * ledger) resolves to {@link RewardTier#BRONZE}.
     */
    public static RewardTier forBalance(int balance) {
        if (balance >= PLATINUM_FLOOR) {
            return RewardTier.PLATINUM;
        }
        if (balance >= GOLD_FLOOR) {
            return RewardTier.GOLD;
        }
        if (balance >= SILVER_FLOOR) {
            return RewardTier.SILVER;
        }
        return RewardTier.BRONZE;
    }
}
