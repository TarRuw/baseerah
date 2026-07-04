package com.baseerah.bank.dto;

import com.baseerah.bank.UnderwritingReport;
import com.baseerah.bank.Verdict;
import com.baseerah.forecast.ForecastEngine.ForecastPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Wire view of a full predictive underwriting report (FR-08, DESIGN.md §5.5, §7.5), served by
 * {@code POST /api/v1/bank/applicants/{id}/report} and consumed by the Step 6.3 report panel: the verdict
 * box, the stamina box, the three KPI boxes (forecast DTI, income stability, 12-month default probability),
 * and the 12-month cash-flow chart.
 *
 * <p>Maps from the internal {@link UnderwritingReport} value plus the applicant's 12-month cash-flow
 * projection — neither the {@code LoanApplication} entity nor the engine's {@code ForecastResult} crosses the
 * boundary (Global Rules). The report itself does not carry the projection points (it keeps only the scalar
 * metrics), so {@code BankService} obtains them through the {@link com.baseerah.forecast.ForecastEngine}
 * interface and this DTO stitches the two together. The three ratio metrics are percentages (see
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
        List<CashFlowPoint> cashFlow) {

    /** One month-end point on the report's cash-flow chart: the {@code date} and the projected {@code balance}. */
    public record CashFlowPoint(LocalDate date, BigDecimal balance) {

        /** Adapt an engine {@link ForecastPoint} to the wire shape. */
        static CashFlowPoint from(ForecastPoint point) {
            return new CashFlowPoint(point.date(), point.projectedBalance());
        }
    }

    /**
     * Stitch an internal {@link UnderwritingReport} together with its 12-month cash-flow projection into the
     * wire report. {@code monthlyPoints} is the month-end-sampled projection (empty for a synthetic applicant
     * with no linked telemetry).
     */
    public static UnderwritingReportDto from(UnderwritingReport report, List<ForecastPoint> monthlyPoints) {
        List<CashFlowPoint> cashFlow = monthlyPoints.stream().map(CashFlowPoint::from).toList();
        return new UnderwritingReportDto(report.applicationId(), report.applicantName(), report.initials(),
                report.purpose(), report.amount(), report.staminaScore(), report.forecastDti(),
                report.incomeStability(), report.defaultProb12mo(), report.verdict(), report.riskTier(),
                cashFlow);
    }
}
