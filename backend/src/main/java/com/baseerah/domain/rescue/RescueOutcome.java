package com.baseerah.domain.rescue;

/**
 * Domain result of confirming a bridge option (FR-07, DESIGN.md §5.4): the stress score before the rescue
 * and the recovered score after the chosen bridge removes the deficit. Returned by {@code
 * RescueService.confirm} and mirrored into the persisted {@code rescue_events} row.
 *
 * <p>A pure domain value (step 10.6): the human-readable confirmation summary is a presentation concern
 * resolved for the request locale in {@code api/rescue/RescueWebMapper}, so this record carries only the
 * two scores. The recovery curve guarantees {@code scoreAfter > scoreBefore} (see {@link RescueRecovery}).
 *
 * @param scoreBefore the client's current stress score, before the rescue
 * @param scoreAfter  the recovered stress score once the bridge is applied ({@code > scoreBefore})
 */
public record RescueOutcome(int scoreBefore, int scoreAfter) {
}
