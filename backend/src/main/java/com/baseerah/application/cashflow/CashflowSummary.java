package com.baseerah.application.cashflow;

import java.math.BigDecimal;

/**
 * Average monthly cash-flow figures for a client over the trailing stress-score window (DESIGN.md §5.1):
 * total credits/debits divided by the number of distinct months that saw activity. Both are on the same
 * 90-day window the Financial Stress Score is computed over, so the Home "average income / spending" cards
 * speak the same period as the gauge and sub-scores above them.
 *
 * @param avgMonthlyIncome  mean monthly credit total, rounded to 2 decimals (SAR)
 * @param avgMonthlyExpense mean monthly debit total, rounded to 2 decimals (SAR)
 */
public record CashflowSummary(BigDecimal avgMonthlyIncome, BigDecimal avgMonthlyExpense) {
}
