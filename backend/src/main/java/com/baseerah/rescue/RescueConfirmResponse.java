package com.baseerah.rescue;

/**
 * API view of a confirmed rescue (FR-07, DESIGN.md §5.4), serialized inside the shared success envelope by
 * {@code POST /api/v1/clients/{id}/rescue/confirm} and consumed by the Step 4.3 recovery card. Mapped from
 * the internal {@link RescueOutcome}; {@code scoreAfter} is always {@code > scoreBefore} (recovery curve in
 * {@link RescueService}). The persisted {@code rescue_events} row is written by the service, not here.
 *
 * @param scoreBefore the client's stress score before the rescue
 * @param scoreAfter  the recovered stress score once the chosen bridge is applied ({@code > scoreBefore})
 * @param message     a short human-readable summary of the rescue for the UI
 */
public record RescueConfirmResponse(int scoreBefore, int scoreAfter, String message) {

    /** Map an internal {@link RescueOutcome} to its wire view. */
    public static RescueConfirmResponse from(RescueOutcome outcome) {
        return new RescueConfirmResponse(outcome.scoreBefore(), outcome.scoreAfter(), outcome.message());
    }
}
