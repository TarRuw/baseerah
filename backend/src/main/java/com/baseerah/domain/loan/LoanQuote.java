package com.baseerah.domain.loan;

import java.math.BigDecimal;

/**
 * Immutable domain result of a loan-affordability simulation (FR-05, DESIGN.md §5.3) — the output of
 * {@link LoanCalculator#compute}. Carries only the computed facts: {@code installment} and {@code total} are
 * rounded to whole SAR (display parity with the DESIGN §8 {@code money()} helper) while the calculator keeps
 * its intermediate math precise; {@code dti} is the ratio {@code (essentials + installment) / income}; the
 * {@link LoanVerdict} is the typed affordability decision; {@code projectedScore} is the stress score after
 * the loan, clamped to {@code [9, 84]}. Band colours and localized verdict text are <em>presentation</em> and
 * are resolved by {@code api/loan/LoanWebMapper}, so the domain stays free of palette and locale.
 *
 * @param installment    monthly instalment in whole SAR
 * @param total          total repayment ({@code installment × term}) in whole SAR
 * @param dti            debt-to-income ratio after the loan (e.g. {@code 0.85})
 * @param verdict        the typed affordability verdict (DESIGN §5.3)
 * @param projectedScore the client's stress score projected after taking the loan, clamped to {@code [9,84]}
 */
public record LoanQuote(
        BigDecimal installment,
        BigDecimal total,
        BigDecimal dti,
        LoanVerdict verdict,
        int projectedScore) {
}
