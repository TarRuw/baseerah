package com.baseerah.domain.financing;

import com.baseerah.domain.bank.Verdict;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A consumer loan/financing request (RFP) and its per-bank proposals. A pure domain value projected from the
 * {@code financing_requests} row plus its {@code financing_proposals} — the JPA entities never cross the web
 * boundary.
 *
 * <p>Phase 12 (unified loan pipeline) folds the FR-08 applicant onto this request: it now also carries a
 * {@code purpose}, an {@link FinancingOrigin origin}, and the per-request underwriting report (stamina,
 * forecast DTI, income stability, 12-month default probability, {@link Verdict}, risk tier). The report
 * fields are {@code null} until a banker underwrites the request (Step 12.2); the three ratio fields are
 * stored as <strong>percentages</strong> (e.g. {@code 34.00} = 34%).
 *
 * @param id              the request id
 * @param clientId        the owning client
 * @param amount          the SAR amount the financing must cover
 * @param deficitInDays   lead time to the deficit captured at creation (audited on choose)
 * @param status          OPEN / ACCEPTED / ACTIVE / CANCELLED / DECLINED
 * @param purpose         the free-text purpose of the request
 * @param origin          how the request was raised (RESCUE / DIRECT)
 * @param staminaScore    underwriting: 12-month cash-flow stamina (null until underwritten)
 * @param forecastDti     underwriting: forecast debt-to-income % (null until underwritten)
 * @param incomeStability underwriting: income stability % (null until underwritten)
 * @param defaultProb12mo underwriting: modelled 12-month default probability % (null until underwritten)
 * @param verdict         underwriting: OK / WARN / BAD (null until underwritten)
 * @param riskTier        underwriting: risk tier label (null until underwritten)
 * @param createdAt       when the request was raised
 * @param proposals       one proposal per targeted bank
 */
public record FinancingRequest(
        UUID id,
        UUID clientId,
        BigDecimal amount,
        int deficitInDays,
        FinancingStatus status,
        String purpose,
        FinancingOrigin origin,
        Integer staminaScore,
        BigDecimal forecastDti,
        BigDecimal incomeStability,
        BigDecimal defaultProb12mo,
        Verdict verdict,
        String riskTier,
        Instant createdAt,
        List<FinancingProposal> proposals) {
}
