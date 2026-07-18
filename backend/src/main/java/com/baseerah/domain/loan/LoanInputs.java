package com.baseerah.domain.loan;

import java.math.BigDecimal;

/**
 * The complete, explicit input set for {@link LoanCalculator#compute} (DESIGN.md §5.3). The three loan
 * parameters the Simulate-screen sliders drive ({@code principal}/{@code rate}/{@code term}) plus the client
 * telemetry the affordability verdict and score impact are measured against ({@code income}/{@code essentials},
 * derived by {@link LoanCalculator#deriveTelemetry}) and the {@code currentScore} the projection starts from.
 * Assembling these here keeps {@code compute} a pure function of one immutable argument — no clock, no database.
 *
 * @param principal    loan amount P (SAR, positive)
 * @param rate         nominal annual interest rate as a percentage (non-negative; {@code 0} → {@code P/term})
 * @param term         repayment term n in months (positive)
 * @param income       the client's monthly income derived from telemetry (SAR)
 * @param essentials   the client's monthly recurring essentials derived from telemetry (SAR)
 * @param currentScore the client's current stress score, before the loan
 */
public record LoanInputs(
        BigDecimal principal,
        BigDecimal rate,
        int term,
        BigDecimal income,
        BigDecimal essentials,
        int currentScore) {
}
