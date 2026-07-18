package com.baseerah.application.bank;

import com.baseerah.domain.bank.RiskPolicy;
import java.math.BigDecimal;
import java.util.List;

/**
 * The computed portfolio (FR-08, DESIGN.md §7.6) the {@link BankService} returns to the web layer: the four
 * KPIs and the monitoring rows over the active book, plus the live {@link RiskPolicy} the controller uses to
 * stamp the compliance surface (residency marker, export gate, and the tokenization toggle governing the per
 * row account tokens). Every figure is computed from the seeded underwriting data — nothing is hardcoded
 * (Global Rules); see {@code BankService} for the derivation and the documented judgment curves.
 *
 * @param activeFacilities count of monitored (underwritten, not-declined) facilities
 * @param avgStamina       mean stamina score across the active book (0–100), rounded
 * @param nplRate          modelled NPL rate of the screened active book, as a percentage
 * @param nplBaselineDelta {@code nplRate} minus the un-screened baseline (≤ 0 = a reduction), percentage points
 * @param atRiskAccounts   count of active-book facilities flagged for attention (status not {@code HEALTHY})
 * @param monitoring       one row per active-book facility (tokens resolved later, in the web layer)
 * @param policy           the live risk policy governing the compliance stamp
 */
public record PortfolioResult(
        int activeFacilities,
        int avgStamina,
        BigDecimal nplRate,
        BigDecimal nplBaselineDelta,
        int atRiskAccounts,
        List<MonitoringRowData> monitoring,
        RiskPolicy policy) {
}
