package com.baseerah.api.loan;

import java.math.BigDecimal;

/**
 * API view of a loan-affordability simulation (FR-05, DESIGN.md §5.3), serialized inside the shared success
 * envelope by {@code POST /api/v1/clients/{id}/loan-simulate} and consumed by the Step 3.5 Simulate screen.
 *
 * <p>This immutable value is projected from the domain {@code LoanQuote} by {@link LoanWebMapper}; no JPA
 * entity crosses the controller boundary (loan simulation is stateless — Global Rules). {@code installment}
 * and {@code total} are rounded to whole SAR for display consistency with the DESIGN §8 {@code money()}
 * helper. {@code dti} is the ratio {@code (essentials+installment)/income}; {@code dtiColor} and
 * {@code verdictColor} are DESIGN §8 palette hexes so the served colour and the rendered band never disagree
 * (the prototype's pastel variants are remapped to the canonical palette). {@code verdict} is the verdict
 * text localized to the request locale (Step 8.1, I18N-01).
 *
 * @param installment    monthly instalment in whole SAR
 * @param total          total repayment ({@code installment × term}) in whole SAR
 * @param dti            debt-to-income ratio after the loan (e.g. {@code 0.85})
 * @param dtiColor       DESIGN §8 hex for the DTI band ({@code <70% green, <90% orange, else red})
 * @param verdict        affordability verdict text, localized to the request locale (DESIGN §5.3)
 * @param verdictColor   DESIGN §8 hex for the verdict band (green / orange / red)
 * @param projectedScore the client's stress score projected after taking the loan, clamped to {@code [9,84]}
 */
public record LoanSimulateResponse(
        BigDecimal installment,
        BigDecimal total,
        BigDecimal dti,
        String dtiColor,
        String verdict,
        String verdictColor,
        int projectedScore) {
}
