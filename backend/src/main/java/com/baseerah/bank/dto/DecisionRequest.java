package com.baseerah.bank.dto;

import com.baseerah.bank.Decision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/bank/applicants/{id}/decision} (FR-08, DESIGN.md §5.5): the human
 * lending outcome a banker records against an underwritten applicant. Validated at the controller edge via
 * {@code @Valid}.
 *
 * <p>Bound as a <em>string</em>, not the {@link Decision} enum, deliberately — the same pattern as
 * {@code RescueConfirmRequest}: an unknown value then fails {@code @Pattern} → a
 * {@code MethodArgumentNotValidException} → the Step 0.4 handler's shared {@code 400 VALIDATION_ERROR}
 * envelope, whereas binding the enum directly would surface an unknown value as an unreadable-body 500 deep
 * inside Jackson. Matching is <strong>case-insensitive</strong> ({@code (?i)}), so {@code "approve"} and
 * {@code "APPROVE"} are both accepted; the controller upper-cases the validated value before
 * {@link Decision#valueOf(String)}.
 *
 * @param decision the recorded outcome — {@code "APPROVE"} or {@code "DECLINE"} (case-insensitive)
 */
public record DecisionRequest(
        @NotNull @Pattern(regexp = "(?i)APPROVE|DECLINE",
                message = "must be APPROVE or DECLINE") String decision) {

    /** The validated string as its {@link Decision} enum (the {@code @Pattern} guarantees a valid name). */
    public Decision toDecision() {
        return Decision.valueOf(decision.toUpperCase(java.util.Locale.ROOT));
    }
}
