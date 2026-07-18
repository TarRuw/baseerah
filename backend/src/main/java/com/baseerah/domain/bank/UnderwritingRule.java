package com.baseerah.domain.bank;

import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.domain.expense.DeclaredExpenseObligations;
import com.baseerah.domain.forecast.ForecastPoint;
import com.baseerah.domain.forecast.ForecastResult;
import com.baseerah.domain.kernel.Direction;
import com.baseerah.domain.kernel.LedgerEntry;
import com.baseerah.domain.stress.StressScoreCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The pure predictive credit-verification rule for the Bank Portal (FR-08, DESIGN.md §5.5) — the functional
 * core extracted from the underwriting shell. For a loan applicant linked to a seeded consumer's telemetry
 * it produces a report — <strong>stamina score</strong> (long-term cash-flow endurance), <strong>forecast
 * DTI</strong>, <strong>income stability</strong>, <strong>12-month default probability</strong>, and a
 * {@link Verdict} — deterministically over its explicit inputs (the applicant request, a trailing ledger
 * window, and a 12-month projection). Framework-free (domain layer): it consumes {@link LedgerEntry} records
 * and a domain {@link ForecastResult}, never a JPA entity or the clock, so the §5.5 verdict bands are
 * unit-tested with no Spring and no database.
 *
 * <p>The one collaborator is the pure {@link StressScoreCalculator} (§5.1), read for income stability and
 * kept consistent with the telemetry aggregation below so an applicant's underwriting agrees with the stress
 * score computed for the same telemetry.
 *
 * <p><strong>Engineer's decisions (DESIGN §5.5 fixes only the verdict thresholds).</strong> Everything below
 * is computed from the client's seeded transactions — no prototype magic numbers (Global Rules):
 * <ul>
 *   <li><b>Stamina (0–100)</b> = a weighted blend of horizon <em>survival</em> (fraction of the 12-month
 *       projection reached before a deficit — 1.0 when none) and buffer <em>retention</em> (how much of the
 *       starting balance survives at the projection's trough). Weights {@value #W_SURVIVAL} /
 *       {@value #W_RETENTION}. A saver who never deficits and keeps most of their buffer scores high; an
 *       early, deep deficit scores low.</li>
 *   <li><b>Forecast DTI (%)</b> = {@code (recurring obligations + requested-loan servicing) / monthly
 *       income}. The requested loan has no rate/term of its own, so its monthly servicing is amortised at a
 *       standard product term ({@value #STANDARD_TERM_MONTHS} mo) and rate ({@value #STANDARD_ANNUAL_RATE_PCT}%
 *       annual) via the §5.3 annuity formula — genuine product parameters, like {@code LoanCalculator}'s rate
 *       input. Zero income underwrites to {@value #DTI_MAX_PERCENT}% (→ BAD).</li>
 *   <li><b>Income stability (%)</b> = the §5.1 income-consistency sub-score × 100.</li>
 *   <li><b>12-month default probability (%)</b> = a weighted risk blend — low stamina, high DTI, and erratic
 *       income each push it up — scaled to a {@value #PD_CEILING_PERCENT}% ceiling. This blend (weights
 *       {@value #W_PD_STAMINA} / {@value #W_PD_DTI} / {@value #W_PD_STABILITY}) is the one purely
 *       judgment-driven curve here; it does <em>not</em> feed the verdict (§5.5 fixes the verdict on stamina
 *       + DTI alone), only the reported risk figure and downstream portfolio NPL metrics.</li>
 *   <li><b>Risk tier</b> = a letter aligned to the verdict: OK→A, WARN→B, BAD→C.</li>
 * </ul>
 */
public class UnderwritingRule {

    // Stamina blend: how far the projection survives, how much buffer it retains at the trough, and which
    // way the balance is heading over the horizon (a steadily-eroding balance is less "enduring" than a
    // rising one of the same trough, so the trend term keeps stamina from over-rewarding a decline).
    private static final double W_SURVIVAL = 0.5;
    private static final double W_RETENTION = 0.3;
    private static final double W_TREND = 0.2;
    /** Relative end-vs-start change that saturates the trend term: +this → 1.0, -this → 0.0, flat → 0.5. */
    private static final double TREND_SATURATION = 0.20;

    // Requested-loan servicing: the application carries only an amount, so amortise it at a standard product
    // term/rate (real product parameters, like LoanCalculator's rate input — not prototype demo constants).
    private static final int STANDARD_TERM_MONTHS = 60;
    private static final double STANDARD_ANNUAL_RATE_PCT = 9.0;

    /** DTI ceiling (%) — caps the persisted figure and is what a zero-income applicant underwrites to. */
    public static final BigDecimal DTI_MAX_PERCENT = new BigDecimal("200.00");

    // 12-month default-probability blend (engineer's curve — see class Javadoc). Weights sum to 1.
    private static final double W_PD_STAMINA = 0.5;
    private static final double W_PD_DTI = 0.3;
    private static final double W_PD_STABILITY = 0.2;
    /** A maximally-risky applicant tops out at this modelled 12-month PD (%); realistic PDs sit well under 100. */
    private static final double PD_CEILING_PERCENT = 60.0;

    // §5.5 verdict thresholds (fixed by DESIGN — not an engineer's choice).
    private static final int OK_STAMINA_FLOOR = 70;
    private static final BigDecimal OK_DTI_CEILING = new BigDecimal("34");
    private static final int BAD_STAMINA_CEILING = 48;
    private static final BigDecimal BAD_DTI_FLOOR = new BigDecimal("71");

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final StressScoreCalculator stressScoreCalculator;

    public UnderwritingRule(StressScoreCalculator stressScoreCalculator) {
        this.stressScoreCalculator = stressScoreCalculator;
    }

    /**
     * The pure §5.5 report core: compute stamina, forecast DTI, income stability, 12-month default
     * probability, verdict and risk tier from an applicant {@code request}, its client's trailing ledger
     * {@code window}, and a 12-month {@code projection}. Deterministic over these inputs (the injected
     * {@link StressScoreCalculator} is itself pure), so the verdict-band tests drive it directly with
     * hand-crafted inputs.
     */
    public UnderwritingReport assemble(ApplicantRequest request, List<LedgerEntry> window,
            ForecastResult projection) {
        return assemble(request, window, List.of(), projection);
    }

    /**
     * The pure §5.5 report core, widened with the client's <strong>declared periodic expenses</strong> (Phase
     * 11 / Step 11.3). Identical to {@link #assemble(ApplicantRequest, List, ForecastResult)} except the
     * declared expenses' monthly total is folded into the recurring obligations that drive the forecast DTI —
     * the <em>same</em> additive treatment, via the <em>same</em> {@link DeclaredExpenseObligations} helper, as
     * the stress score's liability view (so DTI and the stress score stay consistent for the same client).
     * The {@code projection} is expected to already reflect the declared expenses (the forecast shell adds
     * them as scheduled outflows), so stamina does too. Passing an empty list reproduces the feed-only report.
     */
    public UnderwritingReport assemble(ApplicantRequest request, List<LedgerEntry> window,
            List<DeclaredExpense> declared, ForecastResult projection) {
        int stamina = staminaScore(projection);

        Telemetry telemetry = deriveTelemetry(window, declared);
        BigDecimal dtiPercent = forecastDtiPercent(telemetry, request.amount());

        // Income stability is the §5.1 income-consistency sub-score; declared expenses do not touch it, but the
        // list is passed through so the one telemetry definition is used everywhere.
        double stabilityFraction =
                stressScoreCalculator.calculate(window, declared).consistencySubScore();
        BigDecimal stabilityPercent = percent(stabilityFraction);

        BigDecimal defaultProbPercent = defaultProbabilityPercent(stamina, dtiPercent, stabilityFraction);

        Verdict verdict = verdictFor(stamina, dtiPercent);
        String riskTier = tierFor(verdict, defaultProbPercent);

        return new UnderwritingReport(request.applicationId(), request.applicantName(), request.initials(),
                request.purpose(), request.amount(), request.clientRef(), stamina, dtiPercent,
                stabilityPercent, defaultProbPercent, verdict, riskTier);
    }

    /**
     * Overlay the bank's persisted risk policy on a computed report: force an auto-decline (verdict
     * {@link Verdict#BAD}, tier {@code C}) when the applicant's stamina falls below {@code staminaFloor} or the
     * forecast DTI meets/exceeds {@code autoDeclineThreshold} (%). This is the bank's own tunable guardrail on
     * top of the DESIGN §5.5 bands — the same stamina-floor / DTI-ceiling contract the portfolio screening
     * uses — so raising the floor or lowering the ceiling via {@code PUT /api/v1/bank/risk-policy} turns a
     * borderline OK/WARN applicant into an auto-decline on the next {@code report}/{@code decision} call. Pure
     * so the threshold behaviour is unit-tested without Spring. A report already at {@code BAD} is returned
     * unchanged.
     *
     * @param report               the §5.5 report to screen
     * @param staminaFloor         minimum acceptable stamina (0–100); below it → auto-decline
     * @param autoDeclineThreshold forecast-DTI ceiling (%); at/above it → auto-decline
     * @return the report, forced to a declining verdict if the policy is breached; otherwise unchanged
     */
    public static UnderwritingReport enforcePolicy(UnderwritingReport report, int staminaFloor,
            int autoDeclineThreshold) {
        boolean belowFloor = report.staminaScore() < staminaFloor;
        boolean atOrOverThreshold =
                report.forecastDti().compareTo(BigDecimal.valueOf(autoDeclineThreshold)) >= 0;
        if (report.verdict() == Verdict.BAD || (!belowFloor && !atOrOverThreshold)) {
            return report;
        }
        return new UnderwritingReport(report.applicationId(), report.applicantName(), report.initials(),
                report.purpose(), report.amount(), report.clientRef(), report.staminaScore(),
                report.forecastDti(), report.incomeStability(), report.defaultProb12mo(), Verdict.BAD,
                tierFor(Verdict.BAD));
    }

    // ── Stamina ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Long-term cash-flow endurance (0–100) from a 12-month projection: a {@value #W_SURVIVAL}/
     * {@value #W_RETENTION} blend of how far the projection survives before a deficit and how much of the
     * starting buffer remains at its trough. Never crossing zero with a well-kept buffer scores high; an
     * early, deep deficit scores low.
     */
    public static int staminaScore(ForecastResult projection) {
        List<ForecastPoint> points = projection.points();
        LocalDate start = points.get(0).date();
        long horizon = Math.max(1, ChronoUnit.DAYS.between(start, points.get(points.size() - 1).date()));

        double survival;
        if (projection.deficitDate() == null) {
            survival = 1.0;
        } else {
            long daysToDeficit = ChronoUnit.DAYS.between(start, projection.deficitDate());
            survival = clamp01((double) daysToDeficit / horizon);
        }

        double base = points.get(0).projectedBalance().doubleValue();
        double trough = projection.minProjectedBalance().doubleValue();
        double retention;
        if (base > 0) {
            retention = clamp01(trough / base);
        } else {
            // No starting buffer to erode: healthy only if the projection never sinks below zero.
            retention = trough >= 0 ? 1.0 : 0.0;
        }

        // Trend: where the balance ends vs starts over the horizon. A rising balance is more enduring than a
        // falling one that happens to bottom out at the same trough; flat is neutral (0.5).
        double last = points.get(points.size() - 1).projectedBalance().doubleValue();
        double trend;
        if (base > 0) {
            double change = (last - base) / base;
            trend = clamp01(0.5 + change / TREND_SATURATION);
        } else {
            trend = last >= base ? 1.0 : 0.0;
        }

        double blended = W_SURVIVAL * survival + W_RETENTION * retention + W_TREND * trend;
        return (int) Math.max(0, Math.min(100, Math.round(100.0 * blended)));
    }

    // ── Forecast DTI ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Forecast debt-to-income after the requested loan, as a percentage: {@code (obligations + servicing) /
     * monthlyIncome × 100}, capped at {@value #DTI_MAX_PERCENT}%. Zero income underwrites to the cap (→ BAD).
     */
    private BigDecimal forecastDtiPercent(Telemetry telemetry, BigDecimal requestedAmount) {
        if (telemetry.monthlyIncome().signum() <= 0) {
            return DTI_MAX_PERCENT;
        }
        BigDecimal servicing = monthlyServicing(requestedAmount);
        double dtiFraction = telemetry.monthlyObligations().add(servicing).doubleValue()
                / telemetry.monthlyIncome().doubleValue();
        BigDecimal dtiPercent = BigDecimal.valueOf(dtiFraction * 100.0).setScale(2, RoundingMode.HALF_UP);
        return dtiPercent.min(DTI_MAX_PERCENT);
    }

    /** Monthly servicing of the requested amount, amortised at the standard product term/rate (§5.3 annuity). */
    private static BigDecimal monthlyServicing(BigDecimal amount) {
        double p = amount.doubleValue();
        double r = STANDARD_ANNUAL_RATE_PCT / 100.0 / 12.0;
        double installment = (r == 0.0)
                ? p / STANDARD_TERM_MONTHS
                : p * (r / (1.0 - Math.pow(1.0 + r, -STANDARD_TERM_MONTHS)));
        return BigDecimal.valueOf(installment).setScale(2, RoundingMode.HALF_UP);
    }

    // ── 12-month default probability ──────────────────────────────────────────────────────────────────

    /**
     * Modelled 12-month probability of default (%) — a weighted blend where low stamina, high DTI, and
     * erratic income each raise risk, scaled to the {@value #PD_CEILING_PERCENT}% ceiling. See the class
     * Javadoc: this is the one judgment-driven curve, and it does not affect the verdict.
     */
    private static BigDecimal defaultProbabilityPercent(int stamina, BigDecimal dtiPercent,
            double stabilityFraction) {
        double staminaRisk = 1.0 - stamina / 100.0;
        double leverageRisk = clamp01(dtiPercent.doubleValue() / 100.0);
        double stabilityRisk = clamp01(1.0 - stabilityFraction);

        double risk = W_PD_STAMINA * staminaRisk + W_PD_DTI * leverageRisk + W_PD_STABILITY * stabilityRisk;
        return BigDecimal.valueOf(clamp01(risk) * PD_CEILING_PERCENT).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Verdict / tier (§5.5 thresholds — fixed) ──────────────────────────────────────────────────────

    /**
     * The §5.5 verdict from stamina and forecast DTI (%): {@code OK} when {@code stamina >= 70 AND DTI <= 34};
     * {@code BAD} when {@code stamina <= 48 OR DTI >= 71}; otherwise {@code WARN}. OK and BAD are mutually
     * exclusive (an OK applicant can satisfy neither BAD clause), so the order is safe. Public so the band
     * tests can pin each threshold directly.
     */
    public static Verdict verdictFor(int stamina, BigDecimal dtiPercent) {
        if (stamina >= OK_STAMINA_FLOOR && dtiPercent.compareTo(OK_DTI_CEILING) <= 0) {
            return Verdict.OK;
        }
        if (stamina <= BAD_STAMINA_CEILING || dtiPercent.compareTo(BAD_DTI_FLOOR) >= 0) {
            return Verdict.BAD;
        }
        return Verdict.WARN;
    }

    /** Risk tier aligned to the verdict: OK→A (low), WARN→B (medium), BAD→C (high). */
    public static String tierFor(Verdict verdict) {
        return switch (verdict) {
            case OK -> "A";
            case WARN -> "B";
            case BAD -> "C";
        };
    }

    /** Modelled 12-month PD (%) at/above which the tier is bumped one worse; the C floor bumps to C. */
    private static final BigDecimal PD_TIER_B_FLOOR = new BigDecimal("20");
    private static final BigDecimal PD_TIER_C_FLOOR = new BigDecimal("35");

    /**
     * Risk tier from the verdict <em>and</em> the modelled default probability. The verdict sets the floor
     * (OK→A, WARN→B, BAD→C — DTI-driven, never overridden), then a high PD can only <strong>downgrade</strong>
     * it (never upgrade), so an affordable-looking applicant carrying a high modelled default risk cannot sit
     * in a top tier while a decisive DTI failure always stays C.
     */
    public static String tierFor(Verdict verdict, BigDecimal defaultProbPercent) {
        String floor = tierFor(verdict);
        String pdTier = defaultProbPercent.compareTo(PD_TIER_C_FLOOR) >= 0 ? "C"
                : defaultProbPercent.compareTo(PD_TIER_B_FLOOR) >= 0 ? "B" : "A";
        // Worse of the two letters (C > B > A) — downgrade only.
        return floor.compareTo(pdTier) >= 0 ? floor : pdTier;
    }

    // ── Telemetry (mirrors StressScoreCalculator's §5.1 aggregation) ──────────────────────────────────

    /**
     * Derive monthly income and recurring monthly obligations from the window, using the same definitions as
     * {@link StressScoreCalculator} (§5.1): income is mean monthly credit; obligations are debit groups
     * (category + cleansed description) that recur in at least half the window's months — the recurring
     * rent/instalment/subscription load, excluding one-off spend. Keeping these identical means DTI agrees
     * with the stress score's liability view of the same client.
     */
    Telemetry deriveTelemetry(List<LedgerEntry> window) {
        return deriveTelemetry(window, List.of());
    }

    /**
     * Derive monthly income and recurring monthly obligations, widening the obligation figure with the
     * client's <strong>declared periodic expenses</strong> (Phase 11 / Step 11.3). The feed-inferred
     * obligations use the same §5.1 definition as {@link StressScoreCalculator}; the declared expenses' monthly
     * total is then added on top via the shared {@link DeclaredExpenseObligations#monthlyTotal} helper — the
     * <em>same</em> helper the stress calculator uses — so the DTI obligation and the stress liability obligation
     * cannot drift. Passing an empty list reproduces the feed-only telemetry.
     */
    Telemetry deriveTelemetry(List<LedgerEntry> window, List<DeclaredExpense> declared) {
        BigDecimal creditSum = BigDecimal.ZERO;
        Set<YearMonth> months = new TreeSet<>();
        Map<String, RecurringGroup> debitGroups = new LinkedHashMap<>();

        for (LedgerEntry tx : window) {
            if (tx.bookingDate() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(tx.bookingDate().atZone(UTC).toLocalDate());
            months.add(ym);
            BigDecimal amount = tx.amount() == null ? BigDecimal.ZERO : tx.amount().abs();
            if (tx.direction() == Direction.CREDIT) {
                creditSum = creditSum.add(amount);
            } else {
                String key = tx.category().name() + "|"
                        + (tx.descriptionCleansed() == null ? "" : tx.descriptionCleansed().strip()
                                .toLowerCase(Locale.ROOT));
                debitGroups.computeIfAbsent(key, k -> new RecurringGroup()).add(ym, amount);
            }
        }

        int monthCount = Math.max(1, months.size());
        BigDecimal monthlyIncome = creditSum.divide(BigDecimal.valueOf(monthCount), 2, RoundingMode.HALF_UP);

        int recurringThreshold = Math.max(2, (int) Math.ceil(monthCount / 2.0));
        BigDecimal recurringMonthly = BigDecimal.ZERO;
        for (RecurringGroup group : debitGroups.values()) {
            if (group.months.size() >= recurringThreshold) {
                recurringMonthly = recurringMonthly.add(
                        group.total.divide(BigDecimal.valueOf(monthCount), 2, RoundingMode.HALF_UP));
            }
        }
        // Declared expenses are added additively on top of the feed-inferred load (Step 11.3), via the shared
        // parity anchor so this stays identical to the stress calculator's declared contribution.
        recurringMonthly = recurringMonthly.add(DeclaredExpenseObligations.monthlyTotal(declared));
        return new Telemetry(monthlyIncome, recurringMonthly);
    }

    /**
     * The client's total recurring <em>monthly</em> obligation (SAR) after declared expenses — the exact
     * figure this rule folds into the forecast DTI. Exposed so {@code UnderwritingStressParityTest} can assert
     * it agrees with {@link StressScoreCalculator#recurringMonthlyObligations} for the same inputs, turning the
     * deliberate §5.1 duplication into an enforced invariant.
     *
     * @param window   the client's ledger window
     * @param declared the client's declared expenses (empty for the feed-only view)
     * @return recurring feed obligations + active declared monthly total, in SAR
     */
    public BigDecimal recurringMonthlyObligations(List<LedgerEntry> window, List<DeclaredExpense> declared) {
        return deriveTelemetry(window, declared).monthlyObligations();
    }

    /** Monthly income and recurring obligations derived from a client's telemetry (both SAR). */
    record Telemetry(BigDecimal monthlyIncome, BigDecimal monthlyObligations) {
    }

    /** Accumulates a candidate recurring debit group: its total spend and the distinct months it spans. */
    private static final class RecurringGroup {
        private final Set<YearMonth> months = new TreeSet<>();
        private BigDecimal total = BigDecimal.ZERO;

        void add(YearMonth ym, BigDecimal amount) {
            months.add(ym);
            total = total.add(amount);
        }
    }

    // ── Small helpers ─────────────────────────────────────────────────────────────────────────────────

    /** A {@code [0,1]} fraction expressed as a two-decimal percentage. */
    private static BigDecimal percent(double fraction) {
        return BigDecimal.valueOf(clamp01(fraction) * 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}
