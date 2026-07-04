package com.baseerah.rescue;

/**
 * Result of confirming a bridge option (FR-07, DESIGN.md §5.4): the stress score before the rescue and the
 * recovered score after the chosen bridge removes the deficit, plus a human-readable summary. Returned by
 * {@link RescueService#confirm} and mirrored into the persisted {@code rescue_events} row. An internal
 * domain type — Step 4.2 maps it to a wire DTO.
 *
 * @param scoreBefore the client's current stress score, before the rescue
 * @param scoreAfter  the recovered stress score once the bridge is applied ({@code > scoreBefore})
 * @param message     a short summary of the rescue for the UI
 */
public record RescueOutcome(int scoreBefore, int scoreAfter, String message) {
}
