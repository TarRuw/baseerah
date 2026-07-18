package com.baseerah.api.bank.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/bank/financing-requests/{proposalId}/reply} — the offer a bank
 * operator types in: the nominal annual profit rate ({@code rate}, a percentage; {@code 0} = interest-free)
 * and the repayment {@code term} in months. Bean-validated at the controller edge via {@code @Valid} (mirrors
 * the loan-simulate {@code rate}/{@code term} bounds), so an out-of-range value yields the shared
 * {@code 400 VALIDATION_ERROR} envelope.
 *
 * @param rate the profit rate as a percentage (0–100)
 * @param term the repayment term in months (1–600)
 */
public record FinancingReplyRequest(
        @NotNull @PositiveOrZero @DecimalMax("100.0") BigDecimal rate,
        @NotNull @Min(1) @Max(600) Integer term) {
}
