package com.baseerah.domain.gamification;

/**
 * A client's Akhtar-Points state as the application layer returns it (DESIGN.md §5.6): the current points
 * balance — the running sum of the rewards ledger — and the {@link RewardTier} it derives to via
 * {@link TierRule#forBalance(int)}. Pure domain value; the web mapper projects it to the wire {@code RewardsDto}.
 *
 * @param balance the client's current points balance (ledger sum)
 * @param tier    the reward tier the balance falls into
 */
public record RewardsView(int balance, RewardTier tier) {
}
