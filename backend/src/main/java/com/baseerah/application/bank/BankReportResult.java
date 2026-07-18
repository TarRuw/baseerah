package com.baseerah.application.bank;

import com.baseerah.domain.bank.RiskPolicy;
import com.baseerah.domain.bank.UnderwritingReport;
import com.baseerah.domain.forecast.ForecastPoint;
import java.util.List;
import java.util.UUID;

/**
 * The full result of underwriting one applicant (FR-08, DESIGN.md §5.5, §7.5) the {@link BankService} returns
 * to the web layer: the scored {@link UnderwritingReport}, its 12-month month-end cash-flow series, the live
 * {@link RiskPolicy} governing the compliance stamp, and the linked {@code clientRef} so the controller can
 * resolve the applicant's tokenized accounts through the {@code BankComplianceMapper}. Neither the JPA entity
 * nor a raw account id crosses the boundary (Global Rules).
 *
 * @param report    the §5.5 report (scalar metrics, verdict, tier)
 * @param cashFlow  the 12-month projected month-end balance series for the report chart
 * @param policy    the live risk policy governing the compliance stamp
 * @param clientRef linked consumer whose tokenized accounts the web layer resolves, or {@code null}
 */
public record BankReportResult(
        UnderwritingReport report,
        List<ForecastPoint> cashFlow,
        RiskPolicy policy,
        UUID clientRef) {
}
