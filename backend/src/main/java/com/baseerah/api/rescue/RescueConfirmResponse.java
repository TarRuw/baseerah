package com.baseerah.api.rescue;

/**
 * API view of a confirmed rescue (FR-07, DESIGN.md §5.4), serialized inside the shared success envelope by
 * {@code POST /api/v1/clients/{id}/rescue/confirm} and consumed by the recovery card. A plain carrier:
 * {@code RescueWebMapper} projects the domain {@code RescueOutcome} into it, resolving the locale-specific
 * {@code message}. {@code scoreAfter} is always {@code > scoreBefore} (recovery curve in {@code
 * RescueRecovery}). The persisted {@code rescue_events} row is written by the service, not here.
 *
 * @param scoreBefore the client's stress score before the rescue
 * @param scoreAfter  the recovered stress score once the chosen bridge is applied ({@code > scoreBefore})
 * @param message     a short human-readable summary of the rescue for the UI
 */
public record RescueConfirmResponse(int scoreBefore, int scoreAfter, String message) {
}
