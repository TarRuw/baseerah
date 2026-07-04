package com.baseerah.bank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The predictive underwriting report for one applicant (FR-08, DESIGN.md §5.5) — the immutable value
 * {@link UnderwritingService} returns after scoring a {@link LoanApplication} from the linked client's
 * telemetry. It carries the four computed metrics, the derived {@link Verdict} and risk tier, and enough
 * request context (applicant identity, requested {@code amount}) for the Step 6.2 endpoints to serialize a
 * complete report without re-reading the entity — keeping the entity itself inside the service layer
 * (Global Rules).
 *
 * <p>The three ratio metrics are expressed as <strong>percentages</strong>: {@code forecastDti} and
 * {@code defaultProb12mo} are 0–100+ percentages; {@code incomeStability} is a 0–100 percentage of income
 * regularity (100 = perfectly steady). {@code staminaScore} is the 0–100 endurance index.
 *
 * @param applicationId   the underwritten application's id
 * @param applicantName   applicant display name
 * @param initials        applicant initials (avatar text)
 * @param purpose         stated loan purpose
 * @param amount          requested financing amount (SAR)
 * @param clientRef       linked seeded client, or {@code null} for a synthetic applicant
 * @param staminaScore    long-term cash-flow endurance, 0–100 (higher = stronger)
 * @param forecastDti     forecast debt-to-income after the requested loan, as a percentage
 * @param incomeStability income regularity, as a percentage (100 = perfectly steady)
 * @param defaultProb12mo modelled 12-month probability of default, as a percentage
 * @param verdict         the §5.5 verdict
 * @param riskTier        a tier label consistent with the verdict (A / B / C)
 */
public record UnderwritingReport(
        UUID applicationId,
        String applicantName,
        String initials,
        String purpose,
        BigDecimal amount,
        UUID clientRef,
        int staminaScore,
        BigDecimal forecastDti,
        BigDecimal incomeStability,
        BigDecimal defaultProb12mo,
        Verdict verdict,
        String riskTier) {
}
