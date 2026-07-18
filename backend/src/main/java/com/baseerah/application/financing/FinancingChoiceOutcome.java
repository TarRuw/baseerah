package com.baseerah.application.financing;

/**
 * The before/after stress score of choosing a financing proposal. {@code scoreBefore} is the client's current
 * score; {@code scoreAfter} is the genuine loan-engine projection with the chosen proposal's instalment
 * folded in (see the score-semantics note in the feature plan). Consumed by the web layer and the Rescue
 * complete view.
 *
 * @param scoreBefore the client's current stress score (0–100)
 * @param scoreAfter  the projected stress score after taking the chosen financing (0–100)
 */
public record FinancingChoiceOutcome(int scoreBefore, int scoreAfter) {
}
