package com.baseerah.application.bank;

import com.baseerah.application.infrastructure.persistence.bank.RiskPolicyPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.bank.RiskPolicyRepository;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.infrastructure.persistence.expense.DeclaredExpensePersistenceMapper;
import com.baseerah.application.infrastructure.persistence.expense.DeclaredExpenseRepository;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestRepository;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionRepository;
import com.baseerah.application.infrastructure.gateway.forecast.ForecastEngine;
import com.baseerah.domain.bank.ApplicantRequest;
import com.baseerah.domain.bank.RiskPolicy;
import com.baseerah.domain.bank.UnderwritingRule;
import com.baseerah.domain.bank.UnderwritingReport;
import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.domain.forecast.ForecastResult;
import com.baseerah.domain.kernel.LedgerEntry;
import com.baseerah.domain.stress.StressScoreCalculator;
import com.baseerah.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The imperative shell around the pure {@link UnderwritingRule} (FR-08, DESIGN.md §5.5) for the unified loan
 * pipeline (Phase 12). It underwrites a <strong>loan request</strong> — a consumer-originated row of
 * {@code financing_requests} carrying its {@code client_id}, {@code amount} and stated {@code purpose} — and
 * stamps the computed §5.5 report back onto that same request via {@link FinancingRequestJpaEntity#applyReport}.
 *
 * <p><strong>Phase 12 change (Step 12.2).</strong> This replaces the retired FR-08 external-applicant
 * underwriting queue (the {@code loan_applications} table + service, dropped in Step 12.1). There are no more
 * invented applicants: every request is a real client's, so the report is computed from that client's linked
 * telemetry. The pure rule, its verdict bands, and the {@link UnderwritingRule#enforcePolicy risk-policy
 * overlay} are reused <em>unchanged</em>.
 *
 * <p>The pipeline mirrors the original Step 6.1 shell (and {@code StressScoreSnapshotWriter}): load the
 * request → project the client's balance 12 months out (via the {@link ForecastEngine} gateway, so the
 * forecasting strategy stays swappable) → read the trailing {@link StressScoreCalculator#DEFAULT_WINDOW_DAYS}-day
 * ledger window and the client's active declared periodic expenses (Phase 11) → hand all three, plus a
 * request-shaped {@link ApplicantRequest}, to the pure rule → overlay the bank's persisted risk policy →
 * persist the report onto the request. The JPA entities never leave this layer; the returned value is the pure
 * domain {@link UnderwritingReport} (Global Rules).
 *
 * <p>The clock is the injected <em>analytics</em> clock, not the wall clock: it anchors the trailing window
 * and the projection to the frozen mock-telemetry window, so underwriting is deterministic over its inputs and
 * the §5.5 band tests are machine-date-independent (the same technique the stress snapshot writer uses).
 */
@Service
public class UnderwritingService {

    /** The requested loan's amortisation horizon used only for stamina's forward projection (§5.5). */
    private static final int PROJECTION_HORIZON_DAYS = 365;

    private final FinancingRequestRepository requestRepository;
    private final ForecastEngine forecastEngine;
    private final TransactionRepository transactionRepository;
    private final DeclaredExpenseRepository declaredExpenseRepository;
    private final RiskPolicyRepository riskPolicyRepository;
    private final UnderwritingRule underwritingRule;
    private final Clock clock;

    @Autowired
    public UnderwritingService(FinancingRequestRepository requestRepository, ForecastEngine forecastEngine,
            TransactionRepository transactionRepository, DeclaredExpenseRepository declaredExpenseRepository,
            RiskPolicyRepository riskPolicyRepository, UnderwritingRule underwritingRule,
            @Qualifier("analyticsClock") Clock clock) {
        this.requestRepository = requestRepository;
        this.forecastEngine = forecastEngine;
        this.transactionRepository = transactionRepository;
        this.declaredExpenseRepository = declaredExpenseRepository;
        this.riskPolicyRepository = riskPolicyRepository;
        this.underwritingRule = underwritingRule;
        this.clock = clock;
    }

    /**
     * Underwrite a loan request: compute the §5.5 report (stamina, forecast DTI, income stability, 12-month
     * default probability, verdict, risk tier) from the request's linked client telemetry, overlay the bank's
     * risk policy, stamp it onto the {@code financing_requests} row, and return the report.
     *
     * @param requestId the loan request to underwrite
     * @return the computed §5.5 report (after the risk-policy overlay)
     * @throws NotFoundException if no request has that id
     */
    @Transactional
    public UnderwritingReport generateReport(UUID requestId) {
        FinancingRequestJpaEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Loan request not found: " + requestId));

        ClientJpaEntity client = request.getClient();
        UUID clientId = client.getId();

        // 12-month projection for stamina — through the gateway so the forecasting strategy stays swappable and
        // any declared-expense scheduled outflows are already reflected (Phase 11 / Step 11.3).
        ForecastResult projection = forecastEngine.project(clientId, PROJECTION_HORIZON_DAYS);

        // Trailing window for income / recurring-obligation telemetry — the same DEFAULT_WINDOW_DAYS span and
        // analytics-clock anchor the stress score uses, so DTI agrees with the client's stress liability view.
        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(StressScoreCalculator.DEFAULT_WINDOW_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TransactionJpaEntity> window =
                transactionRepository.findByAccount_Client_IdAndBookingDateBetween(clientId, from, to);
        List<LedgerEntry> ledger = TransactionPersistenceMapper.toLedgerEntries(window);

        // Active declared periodic expenses widen the obligation load beyond the feed (Step 11.3) — additive,
        // via the same helper the stress calculator uses.
        List<DeclaredExpense> declared =
                declaredExpenseRepository.findByClient_IdAndActiveTrue(clientId).stream()
                        .map(DeclaredExpensePersistenceMapper::toDomain)
                        .toList();

        ApplicantRequest applicantRequest = new ApplicantRequest(request.getId(), client.getProfileLabel(),
                initials(client.getProfileLabel()), request.getPurpose(), request.getAmount(), clientId);

        UnderwritingReport report = underwritingRule.assemble(applicantRequest, ledger, declared, projection);

        // The bank's tunable guardrail still matters: below the stamina floor or at/above the auto-decline DTI
        // threshold forces BAD / tier C, exactly as in the FR-08 queue.
        RiskPolicy policy = RiskPolicyPersistenceMapper.toDomain(riskPolicyRepository.loadPolicy());
        report = UnderwritingRule.enforcePolicy(report, policy.staminaFloor(), policy.autoDeclineThreshold());

        request.applyReport(report.staminaScore(), report.forecastDti(), report.incomeStability(),
                report.defaultProb12mo(), report.verdict(), report.riskTier());
        requestRepository.save(request);

        return report;
    }

    /**
     * Up-to-two-letter initials from a client's profile label (avatar text), echoed into the report so the web
     * layer can render an applicant chip without re-reading the client — mirrors the retired queue's initials.
     */
    private static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.strip().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }
}
