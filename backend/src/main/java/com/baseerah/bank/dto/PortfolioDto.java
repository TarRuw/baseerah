package com.baseerah.bank.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire view of the bank's monitored loan portfolio (FR-08, DESIGN.md §7.6), served by
 * {@code GET /api/v1/bank/portfolio} and consumed by the Step 6.4 Portfolio screen: four KPI cards plus a
 * monitoring table. Every figure is <strong>computed from the seeded underwriting data</strong> (verdicts,
 * stamina, default probabilities, decisions) against the risk policy — nothing is hardcoded (Global Rules);
 * {@code BankService} owns the derivation and this record is a pure carrier.
 *
 * <p>The <em>active book</em> the KPIs and rows describe is every underwritten applicant not yet declined by
 * a banker — declining an applicant via the decision endpoint drops it out of the portfolio. {@code nplRate}
 * is the active book's mean modelled default probability once the risk policy's auto-decline filter is
 * applied (the disciplined book the bank would actually carry); {@code nplBaselineDelta} is that rate minus
 * the un-screened baseline (the same mean over the whole applied demand), so a negative delta quantifies the
 * NPL reduction Baseerah's pre-emptive screening delivers (DESIGN §1 Vision-2030 anchor). See
 * {@code BankService} for the full derivation and the documented judgment curves.
 *
 * @param activeFacilities  count of monitored (underwritten, not-declined) facilities
 * @param avgStamina        mean stamina score across the active book (0–100), rounded
 * @param nplRate           modelled NPL rate of the screened active book, as a percentage
 * @param nplBaselineDelta  {@code nplRate} minus the un-screened baseline (≤ 0 = a reduction), percentage points
 * @param atRiskAccounts    count of active-book facilities flagged for attention (status not {@code HEALTHY})
 * @param monitoring        one row per active-book facility
 * @param dataResidency     NDMO residency marker for this payload ({@code KSA} when local residency is enforced)
 * @param exportAllowed     whether this payload may leave the residency boundary — {@code false} while NDMO is on
 */
public record PortfolioDto(
        int activeFacilities,
        int avgStamina,
        BigDecimal nplRate,
        BigDecimal nplBaselineDelta,
        int atRiskAccounts,
        List<MonitoringRow> monitoring,
        String dataResidency,
        boolean exportAllowed) {

    /**
     * One row of the portfolio monitoring table (DESIGN §7.6): a borrower, their facility, a health score,
     * a trend arrow, and a status badge.
     *
     * @param borrower          borrower display name
     * @param facility          the facility descriptor (loan purpose)
     * @param health            the facility's health score (its stamina, 0–100)
     * @param trend             health trend relative to the portfolio (drives the ↑/→/↓ arrow)
     * @param status            monitoring status badge
     * @param tokenizedAccounts the borrower's accounts as non-reversible SAMA tokens (§9); empty when the
     *                          {@code tokenization} policy toggle is off — a raw account id never appears here
     */
    public record MonitoringRow(String borrower, String facility, int health, Trend trend, Status status,
            List<String> tokenizedAccounts) {
    }

    /** Health trend relative to the portfolio mean — the table's ↑ / → / ↓ arrow (DESIGN §7.6). */
    public enum Trend {
        /** Health above the portfolio mean by more than the deadband. */
        UP,
        /** Health within the deadband of the portfolio mean. */
        FLAT,
        /** Health below the portfolio mean by more than the deadband. */
        DOWN
    }

    /**
     * Monitoring status badge: Healthy · Watch · At-risk, banded off the facility's health (stamina) score so
     * it always agrees with the shown figure. §5.5-aligned cutoffs — see {@code BankService.statusFor(int)}.
     */
    public enum Status {
        /** Health ≥ 70 (the §5.5 OK stamina floor) — comfortably within policy. */
        HEALTHY,
        /** Health 49–69 — mixed signals, keep watching. */
        WATCH,
        /** Health ≤ 48 (the §5.5 BAD stamina ceiling) — fragile, flagged for action. */
        AT_RISK
    }
}
