package com.baseerah.api.bank;

import com.baseerah.api.bank.dto.LoanRequestRowDto;
import com.baseerah.api.bank.dto.PortfolioDto;
import com.baseerah.api.bank.dto.PortfolioDto.MonitoringRow;
import com.baseerah.api.bank.dto.RiskPolicyDto;
import com.baseerah.api.bank.dto.UnderwritingReportDto;
import com.baseerah.api.bank.dto.UnderwritingReportDto.CashFlowPoint;
import com.baseerah.application.bank.LoanRequestRow;
import com.baseerah.application.bank.MonitoringRowData;
import com.baseerah.application.bank.PortfolioResult;
import com.baseerah.domain.bank.RiskPolicy;
import com.baseerah.domain.bank.UnderwritingReport;
import com.baseerah.domain.forecast.ForecastPoint;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure, static projection of the bank application-layer values (domain views + result records) to the wire
 * DTOs — the {@code api/bank} analogue of {@code StressWebMapper}/{@code ForecastWebMapper}/{@code LoanWebMapper}.
 * The compliance surface (tokens / residency marker / export gate) is resolved by the
 * {@link com.baseerah.application.bank.BankComplianceMapper} in the controller and handed in here, so this
 * mapper stays free of repositories and never sees a raw account id.
 */
public final class BankWebMapper {

    private BankWebMapper() {
    }

    /** Map an application-layer {@link LoanRequestRow} to its underwrite-queue wire view (ids/instant to strings). */
    public static LoanRequestRowDto toLoanRequestRowDto(LoanRequestRow row) {
        return new LoanRequestRowDto(row.requestId().toString(), row.applicantLabel(), row.initials(),
                row.amount(), row.purpose(), row.status().name(),
                row.createdAt() == null ? null : row.createdAt().toString());
    }

    /** Map the domain {@link RiskPolicy} to its wire view. */
    public static RiskPolicyDto toRiskPolicyDto(RiskPolicy policy) {
        return new RiskPolicyDto(policy.staminaFloor(), policy.autoDeclineThreshold(), policy.ndmoResidency(),
                policy.tokenization(), policy.samaLastSync());
    }

    /**
     * Stitch a scored {@link UnderwritingReport} together with its month-end cash-flow projection and the
     * resolved compliance surface into the wire report. {@code monthlyPoints} is empty for a synthetic
     * applicant with no linked telemetry; {@code tokenizedAccounts}/{@code dataResidency}/{@code exportAllowed}
     * come from the {@link com.baseerah.application.bank.BankComplianceMapper} so no raw account id is ever passed in.
     */
    public static UnderwritingReportDto toReportDto(UnderwritingReport report, List<ForecastPoint> monthlyPoints,
            List<String> tokenizedAccounts, String dataResidency, boolean exportAllowed) {
        List<CashFlowPoint> cashFlow = monthlyPoints.stream()
                .map(point -> new CashFlowPoint(point.date(), point.projectedBalance()))
                .toList();
        return new UnderwritingReportDto(report.applicationId(), report.applicantName(), report.initials(),
                report.purpose(), report.amount(), report.staminaScore(), report.forecastDti(),
                report.incomeStability(), report.defaultProb12mo(), report.verdict(), report.riskTier(),
                cashFlow, tokenizedAccounts, dataResidency, exportAllowed);
    }

    /**
     * Map the computed {@link PortfolioResult} to the wire portfolio, resolving each monitoring row's
     * tokenized accounts from the batch-loaded {@code tokensByClient} map (a facility with no linked accounts
     * maps to an empty token list) and stamping the payload's residency marker / export gate.
     */
    public static PortfolioDto toPortfolioDto(PortfolioResult result, Map<UUID, List<String>> tokensByClient,
            String dataResidency, boolean exportAllowed) {
        List<MonitoringRow> monitoring = result.monitoring().stream()
                .map(row -> toMonitoringRow(row, tokensByClient))
                .toList();
        return new PortfolioDto(result.activeFacilities(), result.avgStamina(), result.nplRate(),
                result.nplBaselineDelta(), result.atRiskAccounts(), monitoring, dataResidency, exportAllowed);
    }

    private static MonitoringRow toMonitoringRow(MonitoringRowData row, Map<UUID, List<String>> tokensByClient) {
        List<String> tokens = tokensByClient.getOrDefault(row.clientRef(), List.of());
        return new MonitoringRow(row.borrower(), row.facility(), row.health(), row.trend(), row.status(), tokens);
    }
}
