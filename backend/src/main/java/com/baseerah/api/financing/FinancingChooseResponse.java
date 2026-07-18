package com.baseerah.api.financing;

/**
 * API view of a confirmed financing choice: the before/after stress score, consumed by the Rescue complete
 * view. {@code scoreAfter} is the genuine loan-engine projection with the chosen proposal's instalment folded
 * in (see the feature plan's score-semantics note).
 *
 * @param scoreBefore the client's current stress score (0–100)
 * @param scoreAfter  the projected stress score after taking the chosen financing (0–100)
 */
public record FinancingChooseResponse(int scoreBefore, int scoreAfter) {
}
