package com.baseerah.api.cashflow;

import java.math.BigDecimal;

/**
 * API view of {@code GET /api/v1/clients/{id}/cashflow-summary}: the client's average monthly income and
 * spending (SAR) over the trailing stress-score window. Formatting to {@code SAR}/{@code ر.س} is the
 * client's job.
 *
 * @param avgMonthlyIncome  mean monthly credit total (SAR)
 * @param avgMonthlyExpense mean monthly debit total (SAR)
 */
public record CashflowSummaryResponse(BigDecimal avgMonthlyIncome, BigDecimal avgMonthlyExpense) {
}
