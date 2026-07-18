package com.baseerah.api.gamification.dto;

/**
 * The client's Akhtar-Points state for the Goals screen (FR-10, DESIGN.md §5.6 / §6 {@code GET
 * /clients/{id}/rewards}): the current points balance and the tier it maps to.
 *
 * <p>The field is named {@code riskTier} to match DESIGN §5.6's Goals-screen label and the Step 5.2 contract;
 * its value is the consumer gamification {@link com.baseerah.domain.gamification.RewardTier} (BRONZE…PLATINUM),
 * which is a distinct scale from the bank-side underwriting {@code risk_tier} (Phase 6) — they share no
 * mapping (Step 5.1 handoff).
 *
 * @param points   the client's current points balance (ledger sum)
 * @param riskTier the reward-tier name the balance falls into
 */
public record RewardsDto(int points, String riskTier) {
}
