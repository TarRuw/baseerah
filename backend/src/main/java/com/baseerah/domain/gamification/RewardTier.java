package com.baseerah.domain.gamification;

/**
 * The consumer-facing gamification tier a client reaches as their Akhtar-Points balance grows (DESIGN.md
 * §5.6). Derived purely from the rewards-ledger running sum by {@link TierRule#forBalance(int)} — the same
 * "derive, don't store" posture as {@link com.baseerah.domain.stress.Zone#forScore(int)} — so the tier is
 * always consistent with the ledger and never drifts.
 *
 * <p><strong>Distinct from the bank-side {@code risk_tier}</strong> on loan applications (DESIGN.md §4.2,
 * Phase 6): that one grades underwriting risk; this one is a loyalty/engagement rank surfaced on the Goals
 * screen. They are deliberately not the same scale and share no mapping. A claim awards 50–250 points, so a
 * first claim typically lifts a new saver from BRONZE into SILVER.
 *
 * <p>Pure domain vocabulary — no Spring, JPA, or Jackson. The tier thresholds live on {@link TierRule}.
 */
public enum RewardTier {

    BRONZE,
    SILVER,
    GOLD,
    PLATINUM;
}
