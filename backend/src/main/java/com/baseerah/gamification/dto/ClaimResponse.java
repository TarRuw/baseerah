package com.baseerah.gamification.dto;

/**
 * The result of a successful challenge claim (FR-10, DESIGN.md §6 {@code POST
 * /clients/{id}/challenges/{cid}/claim}): the client's new points balance and tier after the award, plus the
 * claimed challenge in its updated state ({@code claimed = true}) so the Goals screen can flip the card in
 * place without a re-fetch.
 *
 * @param points    the client's new points balance after the award
 * @param riskTier  the reward-tier name the new balance falls into (see {@link RewardsDto})
 * @param challenge the just-claimed challenge, now {@code claimed}
 */
public record ClaimResponse(int points, String riskTier, ChallengeDto challenge) {
}
