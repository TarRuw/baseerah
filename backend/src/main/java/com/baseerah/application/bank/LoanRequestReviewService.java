package com.baseerah.application.bank;

import com.baseerah.application.infrastructure.gateway.forecast.ForecastEngine;
import com.baseerah.application.infrastructure.persistence.bank.RiskPolicyPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.bank.RiskPolicyRepository;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestRepository;
import com.baseerah.domain.bank.RiskPolicy;
import com.baseerah.domain.bank.UnderwritingReport;
import com.baseerah.domain.financing.FinancingStatus;
import com.baseerah.domain.forecast.ForecastPoint;
import com.baseerah.domain.forecast.ForecastResult;
import com.baseerah.shared.NotFoundException;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Bank Portal <strong>underwrite stage</strong> over the unified loan-request model (Phase 12 / Step 12.3):
 * the operator's queue of consumer requests awaiting a risk report, the underwrite action itself, and a
 * per-request decline. This is the request-model successor to the retired FR-08 approve/decline surface
 * ({@code loan_applications} + the old {@code BankService.queue/report/decide}); the three stages now all act
 * on one model — <em>underwrite</em> here, <em>price</em> and <em>disburse</em> via
 * {@code FinancingReviewService}/{@code FinancingDisbursementService}.
 *
 * <p>The scoring itself is delegated <strong>unchanged</strong> to {@link UnderwritingService#generateReport}
 * (the imperative shell around the pure {@code UnderwritingRule}), which stamps the §5.5 report onto the
 * request. This service adds only the presentation wiring the report panel needs on top of the scalar metrics:
 * the 12-month month-end cash-flow chart series and the live risk policy that keys the compliance stamp (the
 * controller resolves the tokenized accounts / residency marker through {@code BankComplianceMapper}). The JPA
 * entity never leaves this layer; the returned values are the pure {@link BankReportResult}/{@link
 * LoanRequestRow} read models (Global Rules).
 */
@Service
public class LoanRequestReviewService {

    /** Horizon (days) for the report's cash-flow chart — a 12-month forward look, matching the FR-08 report. */
    private static final int PROJECTION_HORIZON_DAYS = 365;

    /** The report chart shows one month-end point per calendar month, capped at a year. */
    private static final int MAX_CASHFLOW_POINTS = 12;

    private final FinancingRequestRepository requestRepository;
    private final UnderwritingService underwritingService;
    private final ForecastEngine forecastEngine;
    private final RiskPolicyRepository riskPolicyRepository;

    public LoanRequestReviewService(FinancingRequestRepository requestRepository,
            UnderwritingService underwritingService, ForecastEngine forecastEngine,
            RiskPolicyRepository riskPolicyRepository) {
        this.requestRepository = requestRepository;
        this.underwritingService = underwritingService;
        this.forecastEngine = forecastEngine;
        this.riskPolicyRepository = riskPolicyRepository;
    }

    /**
     * The underwrite-stage queue: consumer requests not yet underwritten ({@code verdict IS NULL}) and still
     * {@link FinancingStatus#OPEN}, oldest first — so the operator works the backlog in arrival order and a
     * declined request never reappears (see {@code findByVerdictIsNullAndStatusOrderByCreatedAtAsc}).
     */
    @Transactional(readOnly = true)
    public List<LoanRequestRow> underwriteQueue() {
        return requestRepository.findByVerdictIsNullAndStatusOrderByCreatedAtAsc(FinancingStatus.OPEN).stream()
                .map(LoanRequestReviewService::toRow)
                .toList();
    }

    /**
     * Underwrite a loan request: run the §5.5 report (stamping it onto the request via the underwriting shell),
     * then assemble the report panel's supporting data — its 12-month month-end cash-flow series and the live
     * risk policy governing the compliance stamp. Returns the full {@link BankReportResult}; the web layer
     * resolves the tokenized-account surface from {@code clientRef} + {@code policy}.
     *
     * @param requestId the loan request to underwrite
     * @return the scored report plus its cash-flow chart, live risk policy, and linked client reference
     * @throws NotFoundException if no request has that id
     */
    @Transactional
    public BankReportResult underwrite(UUID requestId) {
        UnderwritingReport report = underwritingService.generateReport(requestId);
        List<ForecastPoint> cashFlow = monthEndSeries(report.clientRef());
        RiskPolicy policy = RiskPolicyPersistenceMapper.toDomain(riskPolicyRepository.loadPolicy());
        return new BankReportResult(report, cashFlow, policy, report.clientRef());
    }

    /**
     * Decline a loan request at the underwrite stage: move it to {@link FinancingStatus#DECLINED} (the bank
     * rejects the applicant outright). Leaves the report fields untouched — a decline is a lifecycle decision,
     * not a risk report — and returns the updated row so the caller can reflect the new status.
     *
     * @param requestId the loan request to decline
     * @return the request's row, now {@code DECLINED}
     * @throws NotFoundException if no request has that id
     */
    @Transactional
    public LoanRequestRow decline(UUID requestId) {
        FinancingRequestJpaEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Loan request not found: " + requestId));
        request.setStatus(FinancingStatus.DECLINED);
        requestRepository.save(request);
        return toRow(request);
    }

    /**
     * The report chart's 12-month month-end balance series: a single {@link ForecastEngine} projection (a cheap
     * cache lookup — the underwrite step just projected the same client over the same horizon) down-sampled to
     * one point per calendar month (the last projected day in each month), capped at {@value #MAX_CASHFLOW_POINTS}.
     * Empty for a request with no linked client telemetry.
     */
    private List<ForecastPoint> monthEndSeries(UUID clientId) {
        if (clientId == null) {
            return List.of();
        }
        ForecastResult projection = forecastEngine.project(clientId, PROJECTION_HORIZON_DAYS);
        // Chronological points → keep the last of each month (its month-end); LinkedHashMap preserves order.
        Map<YearMonth, ForecastPoint> lastPerMonth = new LinkedHashMap<>();
        for (ForecastPoint point : projection.points()) {
            lastPerMonth.put(YearMonth.from(point.date()), point);
        }
        return lastPerMonth.values().stream().limit(MAX_CASHFLOW_POINTS).toList();
    }

    private static LoanRequestRow toRow(FinancingRequestJpaEntity request) {
        String label = request.getClient().getProfileLabel();
        return new LoanRequestRow(request.getId(), label, initials(label), request.getAmount(),
                request.getPurpose(), request.getStatus(), request.getCreatedAt());
    }

    /** Up-to-two-letter avatar initials from a display label (mirrors the underwriting shell's helper). */
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
