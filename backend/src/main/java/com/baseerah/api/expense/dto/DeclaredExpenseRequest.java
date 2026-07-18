package com.baseerah.api.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Write body for {@code POST}/{@code PUT} of a declared periodic expense (Phase 11 / GitLab backend#1),
 * validated at the controller edge via {@code @Valid} → the shared {@code 400 VALIDATION_ERROR} envelope.
 *
 * <p>The field constraints are the first line of validation; {@code DeclaredExpenseService} re-checks them
 * (so the service is correct on its own) and additionally enforces the one rule bean-validation cannot —
 * that {@code category} resolves to a known {@link com.baseerah.domain.kernel.Category} (with {@code OTHER}
 * an explicitly legal value for declared expenses). {@code currency}/{@code cadence} are optional: omitted →
 * the entity defaults (SAR / MONTHLY); supplied → they must match, since the product is SAR-only and the
 * cadence MONTHLY-only for now.
 *
 * @param label      the user's own words for the expense (Arabic-first), required
 * @param category   the category key (e.g. {@code UTILITIES}, {@code OTHER}), required
 * @param amount     the recurring monthly amount in SAR, strictly positive
 * @param currency   optional; must be {@code SAR} when present
 * @param cadence    optional; must be {@code MONTHLY} when present
 * @param dayOfMonth the recurrence day of month, 1..31
 */
public record DeclaredExpenseRequest(
        @NotBlank String label,
        @NotBlank String category,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0") BigDecimal amount,
        @Pattern(regexp = "SAR", message = "must be SAR") String currency,
        @Pattern(regexp = "MONTHLY", message = "must be MONTHLY") String cadence,
        @Min(1) @Max(31) int dayOfMonth) {
}
