package com.baseerah.rescue;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/clients/{id}/rescue/confirm} (FR-07, DESIGN.md §5.4): which of the
 * two offered bridge options the client chose. Validated at the controller edge via {@code @Valid}.
 *
 * <p>The option is bound as a <em>string</em>, not the {@link RescueOptionType} enum, deliberately: an
 * unknown value then fails {@code @Pattern} → a {@code MethodArgumentNotValidException} → the Step 0.4
 * handler's shared {@code 400 VALIDATION_ERROR} envelope. Binding the enum directly would instead surface an
 * unknown value as an unreadable-body {@code 500} deep inside Jackson, which the shared handler does not
 * classify as validation. The controller resolves the validated string to {@link RescueOptionType} safely.
 *
 * @param option the chosen bridge — must be exactly {@code "MURABAHA"} or {@code "LIQUIDATE"}
 */
public record RescueConfirmRequest(
        @NotNull @Pattern(regexp = "MURABAHA|LIQUIDATE",
                message = "must be MURABAHA or LIQUIDATE") String option) {
}
