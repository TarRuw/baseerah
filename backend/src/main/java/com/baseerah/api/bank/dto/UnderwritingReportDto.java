package com.baseerah.api.bank.dto;

import com.baseerah.domain.bank.UnderwritingReport;
import com.baseerah.domain.bank.Verdict;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Wire view of a full predictive underwriting report (FR-08, DESIGN.md §5.5, §7.5), served by the unified
 * pipeline's {@code POST /api/v1/bank/loan-requests/{id}/underwrite} (Phase 12 / Step 12.3) and consumed by
 * the report panel: the verdict box, the stamina box, the three KPI boxes (forecast DTI, income stability,
 * 12-month default probability), and the 12-month cash-flow chart.
 *
 * <p>Mapped by {@code BankWebMapper} from the internal {@link UnderwritingReport} value plus the applicant's
 * 12-month cash-flow projection and compliance surface — neither the JPA entity nor the engine's
 * {@code ForecastResult} crosses the boundary (Global Rules). The three ratio metrics are percentages (see
 * {@link UnderwritingReport}); {@code cashFlow} reuses the {@link CashFlowPoint} shape of the consumer
 * forecast chart so the front-end can render both with one widget.
 *
 * @param applicationId   the underwritten application's id
 * @param applicantName   applicant display name
 * @param initials        applicant initials (avatar text)
 * @param purpose         stated loan purpose
 * @param amount          requested financing amount (SAR)
 * @param staminaScore    long-term cash-flow endurance, 0–100 (higher = stronger)
 * @param forecastDti     forecast debt-to-income after the requested loan, as a percentage
 * @param incomeStability income regularity, as a percentage (100 = perfectly steady)
 * @param defaultProb12mo modelled 12-month probability of default, as a percentage
 * @param verdict         the §5.5 verdict (also the report's headline risk badge)
 * @param riskTier        tier label (A/B/C) consistent with the verdict
 * @param cashFlow        the 12-month projected month-end balance series driving the report chart
 * @param tokenizedAccounts the applicant's linked accounts as non-reversible SAMA tokens (§9); empty when the
 *                          {@code tokenization} policy toggle is off — a raw account id never appears here
 * @param dataResidency   NDMO residency marker for this payload ({@code KSA} when local residency is enforced)
 * @param exportAllowed   whether this payload may leave the residency boundary — {@code false} while NDMO is on
 */
public record UnderwritingReportDto(
        java.util.UUID applicationId,
        String applicantName,
        String initials,
        String purpose,
        BigDecimal amount,
        int staminaScore,
        BigDecimal forecastDti,
        BigDecimal incomeStability,
        BigDecimal defaultProb12mo,
        Verdict verdict,
        String riskTier,
        List<CashFlowPoint> cashFlow,
        List<String> tokenizedAccounts,
        String dataResidency,
        boolean exportAllowed) {

    /** One month-end point on the report's cash-flow chart: the {@code date} and the projected {@code balance}. */
    public record CashFlowPoint(LocalDate date, BigDecimal balance) {
    }
}
