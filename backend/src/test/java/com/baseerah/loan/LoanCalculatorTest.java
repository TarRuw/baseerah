package com.baseerah.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link LoanCalculator#compute} — the DESIGN §5.3 loan math — with no Spring and no
 * database (the telemetry-deriving shell is exercised separately by the web slice test). Inputs use the
 * prototype's family-persona figures ({@code income 18,500 / essentials 14,200 / surplus 4,300}, current
 * score 62) purely as a <em>known input set</em> to pin the arithmetic; the production path derives these
 * from the client's own transactions (Global Rule), never from constants.
 */
class LoanCalculatorTest {

    private final LoanCalculator calculator = new LoanCalculator(null, null, null, null);

    private static final BigDecimal INCOME = new BigDecimal("18500");
    private static final BigDecimal ESSENTIALS = new BigDecimal("14200"); // surplus = 4,300

    @Test
    void interestFreeLoanSplitsPrincipalEvenlyOverTerm() {
        // r == 0 edge case: installment must be exactly P/n with no divide-by-zero / NaN.
        LoanSimulateResponse quote =
                calculator.compute(new BigDecimal("36000"), BigDecimal.ZERO, 36, INCOME, ESSENTIALS, 62);

        assertThat(quote.installment()).isEqualByComparingTo("1000"); // 36000 / 36
        assertThat(quote.total()).isEqualByComparingTo("36000");      // 1000 * 36
        // DTI = (14200 + 1000) / 18500 = 0.8216; 82% → orange band.
        assertThat(quote.dti().doubleValue()).isCloseTo(0.8216, within(0.0005));
        assertThat(quote.dtiColor()).isEqualTo(LoanCalculator.ORANGE);
        // 1000 <= 0.50 * 4300 (2150) → comfortably affordable, green.
        assertThat(quote.verdict()).isEqualTo(LoanCalculator.VERDICT_COMFORTABLE);
        assertThat(quote.verdictColor()).isEqualTo(LoanCalculator.GREEN);
        // strain = 1000 / 4300 = 0.233 < 0.35 → no penalty → projected stays 62.
        assertThat(quote.projectedScore()).isEqualTo(62);
    }

    @Test
    void amortisedInstalmentMatchesTheStandardFormula() {
        // 50,000 @ 6% / yr over 36 months → ~1,521 SAR/mo (P*r/(1-(1+r)^-n), r = 0.005).
        LoanSimulateResponse quote =
                calculator.compute(new BigDecimal("50000"), new BigDecimal("6"), 36, INCOME, ESSENTIALS, 62);

        assertThat(quote.installment()).isEqualByComparingTo("1521");
        assertThat(quote.total().doubleValue()).isCloseTo(54760.0, within(2.0)); // installment * term
        // 1521 <= 2150 → still comfortably affordable.
        assertThat(quote.verdict()).isEqualTo(LoanCalculator.VERDICT_COMFORTABLE);
        // strain = 1521/4300 = 0.354; penalty ≈ 0.3 → projected ≈ 62.
        assertThat(quote.projectedScore()).isEqualTo(62);
    }

    @Test
    void unaffordableLoanClampsProjectedScoreToTheFloor() {
        // A crushing loan: installment dwarfs the 4,300 surplus → not affordable, projected clamps to 9.
        LoanSimulateResponse quote =
                calculator.compute(new BigDecimal("500000"), new BigDecimal("12"), 12, INCOME, ESSENTIALS, 62);

        assertThat(quote.verdict()).isEqualTo(LoanCalculator.VERDICT_NOT_AFFORDABLE);
        assertThat(quote.verdictColor()).isEqualTo(LoanCalculator.RED);
        assertThat(quote.projectedScore()).isEqualTo(9); // clamp [9, 84] lower bound
    }

    @Test
    void trivialLoanClampsProjectedScoreToTheCeiling() {
        // A tiny loan against a healthy surplus with a high current score → projected clamps to 84.
        LoanSimulateResponse quote = calculator.compute(
                new BigDecimal("1200"), BigDecimal.ZERO, 12,
                new BigDecimal("20000"), new BigDecimal("5000"), 95);

        assertThat(quote.verdict()).isEqualTo(LoanCalculator.VERDICT_COMFORTABLE);
        assertThat(quote.projectedScore()).isEqualTo(84); // clamp [9, 84] upper bound
    }
}
