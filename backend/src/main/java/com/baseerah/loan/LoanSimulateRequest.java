package com.baseerah.loan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/clients/{id}/loan-simulate} (FR-05, DESIGN.md §5.3): the three
 * inputs the Simulate-screen sliders drive (Step 3.5). Validated at the controller edge via {@code @Valid};
 * a violation becomes a {@code MethodArgumentNotValidException} → the Step 0.4 handler's shared
 * {@code 400 VALIDATION_ERROR} envelope.
 *
 * @param principal loan amount (P) in SAR — must be positive
 * @param rate      nominal annual interest rate as a percentage (e.g. {@code 6.5}) — non-negative; {@code 0}
 *                  is the interest-free edge case ({@code installment = P / term})
 * @param term      repayment term (n) in months — must be positive
 */
public record LoanSimulateRequest(
        @NotNull @Positive BigDecimal principal,
        @NotNull @PositiveOrZero BigDecimal rate,
        @NotNull @Positive Integer term) {
}
