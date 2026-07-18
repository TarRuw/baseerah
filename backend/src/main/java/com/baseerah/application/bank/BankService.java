package com.baseerah.application.bank;

import com.baseerah.application.infrastructure.persistence.bank.RiskPolicyJpaEntity;
import com.baseerah.application.infrastructure.persistence.bank.RiskPolicyPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.bank.RiskPolicyRepository;
import com.baseerah.application.infrastructure.persistence.financing.FinancingProposalJpaEntity;
import com.baseerah.application.infrastructure.persistence.financing.FinancingProposalRepository;
import com.baseerah.application.infrastructure.persistence.financing.FinancingRequestJpaEntity;
import com.baseerah.domain.bank.RiskPolicy;
import com.baseerah.domain.bank.Status;
import com.baseerah.domain.bank.Trend;
import com.baseerah.domain.financing.ProposalStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bank Portal orchestration for the singleton risk policy (DESIGN.md §7.7) and the portfolio monitoring
 * status bands (§5.5-aligned, §7.6). Returns pure domain values to the web layer, so the JPA entities never
 * cross the boundary (Global Rules).
 *
 * <p><strong>Phase 12 (unified loan pipeline).</strong> The FR-08 external-applicant underwriting queue
 * ({@code loan_applications}) has been retired: every loan is now a consumer-originated request in the
 * unified {@code financing_requests} model. The queue/report/decide surface that used to live here was dropped
 * in Step 12.1, and Steps 12.2–12.3 rebuilt underwriting on the unified request-with-proposals model. Step
 * 12.4 rebuilds the <strong>portfolio over disbursed facilities</strong> here ({@link #portfolio()}): the
 * active book is no longer verdict-screened applicants but the {@link ProposalStatus#DISBURSED} proposals,
 * each joined to its request's stamped §5.5 figures. The {@link #statusFor(int) health banding} is reused.
 */
@Service
public class BankService {

    /** Health at/above this maps to {@link Status#HEALTHY} — the §5.5 OK stamina floor. */
    private static final int HEALTHY_FLOOR = 70;

    /** Health at/below this maps to {@link Status#AT_RISK} — the §5.5 BAD stamina ceiling. */
    private static final int AT_RISK_CEILING = 48;

    /** A facility's health must differ from the portfolio mean by more than this to earn a ↑/↓ trend arrow. */
    private static final int TREND_DEADBAND = 5;

    private final RiskPolicyRepository riskPolicyRepository;
    private final FinancingProposalRepository proposalRepository;

    public BankService(RiskPolicyRepository riskPolicyRepository,
            FinancingProposalRepository proposalRepository) {
        this.riskPolicyRepository = riskPolicyRepository;
        this.proposalRepository = proposalRepository;
    }

    // ── Portfolio over disbursed facilities ──────────────────────────────────────────────────────────

    /**
     * The monitored loan portfolio (FR-08 / Phase 12, DESIGN.md §7.6) rebuilt over <strong>disbursed
     * facilities</strong>. With external applicants dropped (Step 12.1) and the loan lifecycle now ending in
     * disbursement, the active book is the set of {@link ProposalStatus#DISBURSED} proposals — each joined to
     * its parent {@code financing_requests} row for the stamped §5.5 figures (stamina, 12-month default
     * probability), the borrower ({@code client}) and the facility descriptor ({@code purpose}).
     *
     * <p>KPIs: {@code activeFacilities} is the disbursed count; {@code avgStamina} the rounded mean stamina;
     * {@code nplRate} the mean stamped 12-month default probability (already a percentage, §5.5); and
     * {@code atRiskAccounts} the count of facilities at/below the {@link #AT_RISK_CEILING} stamina ceiling (the
     * {@link Status#AT_RISK} band). {@code nplBaselineDelta} is dropped to {@code 0} — the old
     * "vs. underwrite-everyone baseline" delta compared the screened book against the full applied demand, but
     * a disbursed-only book has no un-screened population to compare against, so the reduction figure is no
     * longer meaningful here. Each row bands its status off {@link #statusFor(int)} and its {@link Trend} off
     * the facility's distance from the portfolio mean (±{@value #TREND_DEADBAND}); the borrower's
     * {@code clientRef} is carried so the web layer can resolve tokenized accounts through
     * {@code BankComplianceMapper} (no raw account id crosses the boundary — Global Rules).
     *
     * <p>Read within the transaction so the lazy {@code proposal → request → client} associations resolve; the
     * returned {@link PortfolioResult} is a pure value (the JPA entities never leave this layer). Disbursed
     * facilities always carry a stamped report (the pipeline underwrites before pricing; the demo seeder
     * underwrites before disbursing), so the stamina/default-probability reads are non-null.
     */
    @Transactional(readOnly = true)
    public PortfolioResult portfolio() {
        RiskPolicy policy = RiskPolicyPersistenceMapper.toDomain(riskPolicyRepository.loadPolicy());
        List<FinancingProposalJpaEntity> facilities =
                proposalRepository.findByStatusOrderByCreatedAtAsc(ProposalStatus.DISBURSED);

        int activeFacilities = facilities.size();
        if (facilities.isEmpty()) {
            return new PortfolioResult(0, 0, zero(), zero(), 0, List.of(), policy);
        }

        double meanStamina = facilities.stream()
                .mapToInt(f -> f.getRequest().getStaminaScore())
                .average()
                .orElse(0);
        int avgStamina = (int) Math.round(meanStamina);

        BigDecimal nplRate = facilities.stream()
                .map(f -> f.getRequest().getDefaultProb12mo())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(activeFacilities), 2, RoundingMode.HALF_UP);

        int atRiskAccounts = (int) facilities.stream()
                .filter(f -> f.getRequest().getStaminaScore() <= AT_RISK_CEILING)
                .count();

        List<MonitoringRowData> monitoring = facilities.stream()
                .map(f -> toMonitoringRow(f, meanStamina))
                .toList();

        return new PortfolioResult(activeFacilities, avgStamina, nplRate, zero(), atRiskAccounts, monitoring,
                policy);
    }

    /** One monitoring row per disbursed facility, banded off its stamped stamina and the portfolio mean. */
    private static MonitoringRowData toMonitoringRow(FinancingProposalJpaEntity facility, double portfolioMean) {
        FinancingRequestJpaEntity request = facility.getRequest();
        int health = request.getStaminaScore();
        return new MonitoringRowData(request.getClient().getProfileLabel(), request.getPurpose(), health,
                trendFor(health, portfolioMean), statusFor(health), request.getClient().getId());
    }

    /** Health trend relative to the portfolio mean: ↑/↓ only when the gap exceeds the {@code ±} deadband. */
    private static Trend trendFor(int health, double portfolioMean) {
        double delta = health - portfolioMean;
        if (delta > TREND_DEADBAND) {
            return Trend.UP;
        } else if (delta < -TREND_DEADBAND) {
            return Trend.DOWN;
        } else {
            return Trend.FLAT;
        }
    }

    /** A zero money/percentage figure at the wire scale (2 dp), used for an empty book and the retired delta. */
    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    // ── Portfolio banding ────────────────────────────────────────────────────────────────────────────

    /**
     * Monitoring status banded off a facility's {@code health} (stamina) score, so the badge always agrees
     * with the number shown beside it (UI-06). Cutoffs are §5.5-aligned: {@link Status#HEALTHY} at/above the
     * OK stamina floor ({@value #HEALTHY_FLOOR}), {@link Status#AT_RISK} at/below the BAD stamina ceiling
     * ({@value #AT_RISK_CEILING}), {@link Status#WATCH} in the mixed band between. Package-private so the
     * boundary mapping is unit-testable without a Spring/Postgres context; the rebuilt portfolio (Step 12.4)
     * reuses it.
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

    // ── Risk policy ──────────────────────────────────────────────────────────────────────────────────

    /** The singleton bank-wide risk policy (DESIGN §7.7). */
    @Transactional(readOnly = true)
    public RiskPolicy riskPolicy() {
        return RiskPolicyPersistenceMapper.toDomain(riskPolicyRepository.loadPolicy());
    }

    /**
     * Update the singleton risk policy from the edited fields and return the persisted result, so a subsequent
     * read round-trips every written field (DESIGN §7.7).
     */
    @Transactional
    public RiskPolicy updateRiskPolicy(int staminaFloor, int autoDeclineThreshold, boolean ndmoResidency,
            boolean tokenization, Instant samaLastSync) {
        RiskPolicyJpaEntity policy = riskPolicyRepository.loadPolicy();
        policy.setStaminaFloor(staminaFloor);
        policy.setAutoDeclineThreshold(autoDeclineThreshold);
        policy.setNdmoResidency(ndmoResidency);
        policy.setTokenization(tokenization);
        policy.setSamaLastSync(samaLastSync);
        riskPolicyRepository.save(policy);
        return RiskPolicyPersistenceMapper.toDomain(policy);
    }
}
