package com.baseerah.bank;

import com.baseerah.bank.dto.ApplicantDto;
import com.baseerah.bank.dto.PortfolioDto;
import com.baseerah.bank.dto.PortfolioDto.MonitoringRow;
import com.baseerah.bank.dto.PortfolioDto.Status;
import com.baseerah.bank.dto.PortfolioDto.Trend;
import com.baseerah.bank.dto.RiskPolicyDto;
import com.baseerah.bank.dto.UnderwritingReportDto;
import com.baseerah.common.ConflictException;
import com.baseerah.common.NotFoundException;
import com.baseerah.forecast.ForecastEngine;
import com.baseerah.forecast.ForecastEngine.ForecastPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bank Portal orchestration (FR-08, DESIGN.md §5.5, §7.5–§7.7). Owns everything the {@link BankController}
 * exposes over {@code /api/v1/bank/*}: the underwriting queue, on-demand report generation, the human
 * approve/decline decision, the portfolio KPIs + monitoring table, and read/update of the singleton risk
 * policy. Keeps the controller thin and maps entities to wire DTOs here, so {@link LoanApplication} /
 * {@link RiskPolicy} never cross the boundary (Global Rules).
 *
 * <p><strong>Report reuse.</strong> {@link #report} delegates the actual scoring to
 * {@link UnderwritingService} (which itself projects through the {@link ForecastEngine} interface and reuses
 * the §5.1 stress-score aggregation), then obtains the 12-month cash-flow chart series through the same
 * {@link ForecastEngine} interface — never {@code HeuristicForecast} directly — so a forecasting sidecar can
 * replace both without touching this service.
 *
 * <p><strong>Portfolio derivation (engineer's judgment curves — everything computed from seeded telemetry,
 * no hardcoded metrics, Global Rules).</strong> The <em>active book</em> is every underwritten applicant a
 * banker has not declined; declining one via {@link #decide} drops it from the portfolio. Over that book:
 * <ul>
 *   <li><b>Active facilities</b> = the active book's size; <b>average stamina</b> = its mean stamina.</li>
 *   <li><b>NPL rate (%)</b> = the mean modelled 12-month default probability of the book <em>once the risk
 *       policy's auto-decline filter is applied</em> (the disciplined book the bank actually carries).
 *       <b>Baseline delta</b> = that rate minus the same mean over the <em>whole, unscreened</em> active book
 *       (the "approve-everyone" counterfactual) — a negative delta quantifies the NPL reduction Baseerah's
 *       pre-emptive screening delivers (DESIGN §1). With no time series on a frozen mock this
 *       screened-vs-unscreened contrast is the documented, data-driven stand-in for a period-over-period
 *       delta.</li>
 *   <li><b>At-risk accounts</b> = active-book facilities whose status is {@link Status#AT_RISK}.</li>
 *   <li><b>Monitoring rows</b> — one per active-book facility: {@code health} is its stamina; {@code status}
 *       is banded off that same {@code health} score (§5.5-aligned: Healthy ≥ {@value #HEALTHY_FLOOR},
 *       At-risk ≤ {@value #AT_RISK_CEILING}, Watch between) so the badge and the shown figure never
 *       disagree (see {@link #statusFor(int)}); {@code trend} compares the
 *       facility's health to the portfolio mean with a {@value #TREND_DEADBAND} point deadband (above →
 *       UP, below → DOWN, within → FLAT). The deadband width is the one free presentation parameter here.</li>
 * </ul>
 */
@Service
public class BankService {

    /** 12-month horizon (days) for the report cash-flow chart — same horizon the stamina score uses (§5.2). */
    private static final int REPORT_HORIZON_DAYS = 365;

    /** Months of cash-flow shown on the report chart (DESIGN §7.5 "12-mo cashflow chart"). */
    private static final int CASH_FLOW_MONTHS = 12;

    /** A monitoring facility's health must differ from the portfolio mean by more than this to trend UP/DOWN. */
    private static final int TREND_DEADBAND = 5;

    /** Health at/above this maps to {@link Status#HEALTHY} — the §5.5 OK stamina floor. */
    private static final int HEALTHY_FLOOR = 70;

    /** Health at/below this maps to {@link Status#AT_RISK} — the §5.5 BAD stamina ceiling. */
    private static final int AT_RISK_CEILING = 48;

    private final UnderwritingService underwritingService;
    private final ForecastEngine forecastEngine;
    private final LoanApplicationRepository loanApplicationRepository;
    private final RiskPolicyRepository riskPolicyRepository;
    private final BankComplianceMapper complianceMapper;

    public BankService(UnderwritingService underwritingService, ForecastEngine forecastEngine,
            LoanApplicationRepository loanApplicationRepository, RiskPolicyRepository riskPolicyRepository,
            BankComplianceMapper complianceMapper) {
        this.underwritingService = underwritingService;
        this.forecastEngine = forecastEngine;
        this.loanApplicationRepository = loanApplicationRepository;
        this.riskPolicyRepository = riskPolicyRepository;
        this.complianceMapper = complianceMapper;
    }

    // ── Queue ────────────────────────────────────────────────────────────────────────────────────────

    /** The underwriting queue, oldest applicant first (DESIGN §7.5). */
    @Transactional(readOnly = true)
    public List<ApplicantDto> queue() {
        return loanApplicationRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(ApplicantDto::from)
                .toList();
    }

    // ── Report ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * Generate and persist the predictive report for an applicant, and return it with the 12-month cash-flow
     * series for the chart. Delegates scoring to {@link UnderwritingService} and pulls the chart series from
     * the {@link ForecastEngine} interface.
     *
     * @param applicationId the applicant to underwrite
     * @return the full report DTO including month-end cash-flow points
     * @throws NotFoundException if no such application exists
     * @throws ConflictException if the applicant has no linked client telemetry to underwrite
     */
    @Transactional
    public UnderwritingReportDto report(UUID applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Loan application not found: " + applicationId));
        if (app.getClientRef() == null) {
            throw new ConflictException(
                    "Applicant " + applicationId + " has no linked telemetry to underwrite.");
        }

        UnderwritingReport report = underwritingService.generateReport(applicationId);
        List<ForecastPoint> cashFlow = monthlyCashFlow(app.getClientRef());

        // Compliance surface (SAMA tokenization + NDMO residency, §9) — resolved through the central mapper so
        // only tokenized account references, never a raw account id, reach the bank-side DTO.
        RiskPolicy policy = riskPolicyRepository.loadPolicy();
        List<String> tokens = complianceMapper.accountTokensFor(app.getClientRef(), policy);
        return UnderwritingReportDto.from(report, cashFlow, tokens,
                complianceMapper.residencyMarker(policy), complianceMapper.exportAllowed(policy));
    }

    /**
     * The applicant's projected balance sampled to one month-end point per month, capped at
     * {@value #CASH_FLOW_MONTHS} months — the report chart's series. Uses the {@link ForecastEngine}
     * interface so the projection stays swappable.
     */
    private List<ForecastPoint> monthlyCashFlow(UUID clientRef) {
        List<ForecastPoint> daily = forecastEngine.project(clientRef, REPORT_HORIZON_DAYS).points();
        // Keep the last projected point of each calendar month (its month-end balance), in chronological
        // order, then take the first 12 months of the horizon.
        Map<YearMonth, ForecastPoint> byMonth = new LinkedHashMap<>();
        for (ForecastPoint point : daily) {
            byMonth.put(YearMonth.from(point.date()), point);
        }
        return byMonth.values().stream().limit(CASH_FLOW_MONTHS).toList();
    }

    // ── Decision ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * Record a banker's approve/decline decision on an applicant and return the updated queue view.
     *
     * @param applicationId the applicant decided on
     * @param decision      the recorded outcome
     * @return the updated applicant DTO (reflecting the persisted decision)
     * @throws NotFoundException if no such application exists
     */
    @Transactional
    public ApplicantDto decide(UUID applicationId, Decision decision) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Loan application not found: " + applicationId));
        app.setDecision(decision);
        loanApplicationRepository.save(app);
        return ApplicantDto.from(app);
    }

    // ── Portfolio ────────────────────────────────────────────────────────────────────────────────────

    /** The monitored portfolio: 4 KPIs + monitoring rows over the active book (see the class Javadoc). */
    @Transactional(readOnly = true)
    public PortfolioDto portfolio() {
        RiskPolicy policy = riskPolicyRepository.loadPolicy();
        List<LoanApplication> activeBook = loanApplicationRepository.findByVerdictNotNull().stream()
                .filter(app -> app.getDecision() != Decision.DECLINE)
                .toList();

        int activeFacilities = activeBook.size();
        int avgStamina = meanStamina(activeBook);

        // NPL: the screened book we would actually carry vs the whole applied book (approve-everyone).
        BigDecimal baselineNpl = meanDefaultProb(activeBook);
        List<LoanApplication> screened = activeBook.stream()
                .filter(app -> policyEligible(app, policy))
                .toList();
        BigDecimal nplRate = meanDefaultProb(screened);
        BigDecimal nplBaselineDelta = nplRate.subtract(baselineNpl);

        // Resolve every facility's compliance tokens in one query (Step 7.3 — was an N+1: one account lookup
        // per monitoring row). The mapper stays the sole account→token choke-point.
        Map<UUID, List<String>> tokensByClient = complianceMapper.accountTokensFor(
                activeBook.stream().map(LoanApplication::getClientRef).toList(), policy);
        List<MonitoringRow> monitoring = activeBook.stream()
                .map(app -> monitoringRow(app, avgStamina, tokensByClient))
                .toList();
        int atRiskAccounts = (int) monitoring.stream()
                .filter(row -> row.status() == Status.AT_RISK)
                .count();

        return new PortfolioDto(activeFacilities, avgStamina, nplRate, nplBaselineDelta, atRiskAccounts,
                monitoring, complianceMapper.residencyMarker(policy), complianceMapper.exportAllowed(policy));
    }

    /** Whether an underwritten application passes the risk policy (stamina floor and DTI auto-decline). */
    private static boolean policyEligible(LoanApplication app, RiskPolicy policy) {
        return app.getStaminaScore() >= policy.getStaminaFloor()
                && app.getForecastDti().compareTo(BigDecimal.valueOf(policy.getAutoDeclineThreshold())) < 0;
    }

    /**
     * One monitoring row for a facility, with its trend taken relative to the portfolio's mean stamina and the
     * borrower's accounts carried as tokenized references only (via {@link BankComplianceMapper}).
     */
    private MonitoringRow monitoringRow(LoanApplication app, int portfolioMeanStamina,
            Map<UUID, List<String>> tokensByClient) {
        int health = app.getStaminaScore();
        Trend trend;
        if (health > portfolioMeanStamina + TREND_DEADBAND) {
            trend = Trend.UP;
        } else if (health < portfolioMeanStamina - TREND_DEADBAND) {
            trend = Trend.DOWN;
        } else {
            trend = Trend.FLAT;
        }
        // Pre-resolved tokens from the batch load; a facility with no linked accounts maps to an empty list.
        List<String> tokens = tokensByClient.getOrDefault(app.getClientRef(), List.of());
        return new MonitoringRow(app.getApplicantName(), app.getPurpose(), health, trend,
                statusFor(health), tokens);
    }

    /**
     * Monitoring status banded off the facility's {@code health} (stamina) score, so the badge always agrees
     * with the number shown beside it (UI-06). Cutoffs are §5.5-aligned: {@link Status#HEALTHY} at/above the
     * OK stamina floor ({@value #HEALTHY_FLOOR}), {@link Status#AT_RISK} at/below the BAD stamina ceiling
     * ({@value #AT_RISK_CEILING}), {@link Status#WATCH} in the mixed band between. Package-private so the
     * boundary mapping is unit-testable without a Spring/Postgres context.
     */
    static Status statusFor(int health) {
        if (health >= HEALTHY_FLOOR) {
            return Status.HEALTHY;
        } else if (health <= AT_RISK_CEILING) {
            return Status.AT_RISK;
        } else {
            return Status.WATCH;
        }
    }

    /** Mean stamina across the book, rounded to a whole score; {@code 0} for an empty book. */
    private static int meanStamina(List<LoanApplication> book) {
        if (book.isEmpty()) {
            return 0;
        }
        double mean = book.stream().mapToInt(LoanApplication::getStaminaScore).average().orElse(0.0);
        return (int) Math.round(mean);
    }

    /** Mean 12-month default probability (%) across the book; {@code 0.00} for an empty book. */
    private static BigDecimal meanDefaultProb(List<LoanApplication> book) {
        if (book.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = book.stream()
                .map(LoanApplication::getDefaultProb12mo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(book.size()), 2, RoundingMode.HALF_UP);
    }

    // ── Risk policy ──────────────────────────────────────────────────────────────────────────────────

    /** The singleton bank-wide risk policy (DESIGN §7.7). */
    @Transactional(readOnly = true)
    public RiskPolicyDto riskPolicy() {
        return RiskPolicyDto.from(riskPolicyRepository.loadPolicy());
    }

    /**
     * Update the singleton risk policy from an edited DTO and return the persisted result, so a subsequent
     * read round-trips every written field (DESIGN §7.7).
     */
    @Transactional
    public RiskPolicyDto updateRiskPolicy(RiskPolicyDto dto) {
        RiskPolicy policy = riskPolicyRepository.loadPolicy();
        policy.setStaminaFloor(dto.staminaFloor());
        policy.setAutoDeclineThreshold(dto.autoDeclineThreshold());
        policy.setNdmoResidency(dto.ndmoResidency());
        policy.setTokenization(dto.tokenization());
        policy.setSamaLastSync(dto.samaLastSync());
        riskPolicyRepository.save(policy);
        return RiskPolicyDto.from(policy);
    }
}
