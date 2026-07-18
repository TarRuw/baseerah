package com.baseerah.domain.loan;

import com.baseerah.domain.kernel.Direction;
import com.baseerah.domain.kernel.LedgerEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Interactive loan affordability (FR-05, DESIGN.md §5.3) — the pure functional core, mirroring the
 * prototype's math exactly with <strong>no Spring, no database, no clock</strong>. The imperative shell that
 * resolves the client, loads the transaction window and reads the current stress score lives in
 * {@code application/loan/LoanService}; this class is a pure function of its explicit inputs, so the §5.3
 * arithmetic is unit-tested directly. Registered as a Spring bean by {@code config/DomainConfig} so the
 * application service can inject it without the domain depending on Spring (the stress pilot's
 * calculator-as-bean pattern, step 10.2).
 *
 * <p>Two pure operations:
 * <ul>
 *   <li>{@link #deriveTelemetry} aggregates a client's ledger window into monthly {@code income}/{@code
 *       essentials} — <em>the</em> place the prototype's hardcoded demo constants are replaced by real,
 *       per-client figures (Global Rule; DESIGN §5.3).</li>
 *   <li>{@link #compute} runs the §5.3 amortisation, affordability verdict, DTI and score-impact over an
 *       explicit {@link LoanInputs}.</li>
 * </ul>
 *
 * <p><strong>No prototype constants.</strong> The prototype hardcodes the family persona's
 * {@code income=18500 / essentials=14200 / surplus=4300} as demo stand-ins; the real build derives income
 * and essentials from the client's own seeded transactions (Global Rule; DESIGN §5.3).
 *
 * <p><strong>No palette / locale.</strong> {@link #compute} emits a typed {@link LoanVerdict}; the DESIGN §8
 * band colours and the locale-resolved verdict text belong to the web layer ({@code api/loan/LoanWebMapper}),
 * mirroring how {@code StressWebMapper} owns the gauge colour.
 */
public class LoanCalculator {

    // Score-impact curve (DESIGN §5.3): proj = score - max(0, (strain - 0.35)) * 90, clamped to [9, 84].
    private static final double STRAIN_FREE_THRESHOLD = 0.35;
    private static final double STRAIN_PENALTY_SLOPE = 90.0;
    private static final int PROJECTED_SCORE_MIN = 9;
    private static final int PROJECTED_SCORE_MAX = 84;

    // Verdict thresholds as fractions of surplus (DESIGN §5.3).
    private static final BigDecimal COMFORTABLE_FRACTION = new BigDecimal("0.50");
    private static final BigDecimal STRAINS_FRACTION = new BigDecimal("0.85");

    // Recurring-cadence detection for telemetry (mirrors HeuristicForecast §5.2).
    private static final int MONTHLY_DAYS = 30;
    private static final int CADENCE_TOLERANCE_DAYS = 7;
    private static final int MIN_RECURRING_OCCURRENCES = 2;

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    /**
     * Pure DESIGN §5.3 loan math over an explicit {@link LoanInputs} — no clock, no database. Reproduces the
     * prototype: amortised instalment (with the {@code r == 0} interest-free edge case), affordability verdict
     * against the client's surplus, DTI after the loan, and the projected stress-score impact.
     *
     * @param inputs the loan parameters and the client telemetry to measure them against
     * @return the loan quote with instalment/total (whole SAR), DTI, typed verdict and projected score
     */
    public LoanQuote compute(LoanInputs inputs) {
        BigDecimal principal = inputs.principal();
        int term = inputs.term();
        BigDecimal income = inputs.income();
        BigDecimal essentials = inputs.essentials();

        // r = rate/100/12; installment = P*r / (1 - (1+r)^-n), with r == 0 → P/n (no divide-by-zero).
        double r = inputs.rate().doubleValue() / 100.0 / 12.0;
        BigDecimal installment = (r == 0.0)
                ? principal.divide(BigDecimal.valueOf(term), 10, RoundingMode.HALF_UP)
                : principal.multiply(BigDecimal.valueOf(r / (1.0 - Math.pow(1.0 + r, -term))));

        BigDecimal surplus = income.subtract(essentials);

        // Verdict: installment vs fractions of surplus. When surplus <= 0 every comparison fails → not affordable.
        LoanVerdict verdict;
        if (installment.compareTo(surplus.multiply(COMFORTABLE_FRACTION)) <= 0) {
            verdict = LoanVerdict.COMFORTABLE;
        } else if (installment.compareTo(surplus.multiply(STRAINS_FRACTION)) <= 0) {
            verdict = LoanVerdict.STRAINS;
        } else {
            verdict = LoanVerdict.NOT_AFFORDABLE;
        }

        // DTI = (essentials + installment) / income (ratio). Guard a degenerate zero income defensively.
        BigDecimal dti = income.signum() > 0
                ? essentials.add(installment).divide(income, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        // Score impact: strain = installment/surplus; proj = score - max(0, strain - 0.35)*90, clamp [9,84].
        double strain = surplus.signum() > 0
                ? installment.doubleValue() / surplus.doubleValue()
                : Double.MAX_VALUE;
        long rawProjected = Math.round(inputs.currentScore()
                - Math.max(0.0, strain - STRAIN_FREE_THRESHOLD) * STRAIN_PENALTY_SLOPE);
        int projectedScore = (int) Math.max(PROJECTED_SCORE_MIN, Math.min(PROJECTED_SCORE_MAX, rawProjected));

        // Round money to whole SAR for display; total = precise installment × term, then rounded.
        BigDecimal installmentSar = installment.setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalSar = installment.multiply(BigDecimal.valueOf(term)).setScale(0, RoundingMode.HALF_UP);

        return new LoanQuote(installmentSar, totalSar, dti, verdict, projectedScore);
    }

    /**
     * Derive the client's monthly {@code income} and recurring {@code essentials} from their ledger
     * telemetry — <em>the</em> place the prototype's hardcoded demo constants are replaced by real,
     * per-client figures (Global Rule; DESIGN §5.3).
     *
     * <p>Recurring detection mirrors {@link com.baseerah.domain.forecast.BalanceProjection}'s §5.2 cadence
     * concept (Step 3.1): group by {@code direction + category + cleansed description}, and treat a group as a
     * monthly flow when its occurrences repeat on a ~monthly cadence (median inter-occurrence gap within
     * {@value #CADENCE_TOLERANCE_DAYS} days of {@value #MONTHLY_DAYS}). This is deliberately the
     * cadence-gap test, not mere month-coverage: it keeps clean monthly items (salary, rent, instalments,
     * subscriptions) while rejecting frequent bursty spend (groceries, ride-hailing) whose tiny median gap
     * makes it discretionary — exactly what "essentials" should exclude. Each recurring group contributes
     * its mean per-occurrence amount (its typical monthly magnitude), matching the forecast engine's
     * {@code typicalAmount}.
     *
     * <p>{@code income} is the sum of recurring inflows' typical amounts; when a client has no clean
     * recurring inflow (e.g. an irregular-income freelancer), it falls back to mean monthly total credit so
     * the ratio stays meaningful. {@code essentials} is the sum of recurring debits' typical amounts (0 when
     * none). The logic is re-expressed here rather than extracted from the completed forecast engine to
     * avoid modifying a previous step's tested code (the step's "where practical" reuse guidance).
     *
     * @param window the client's trailing ledger window (may be empty); the shell decides the window length
     * @return the derived monthly income and essentials
     */
    public LoanTelemetry deriveTelemetry(List<LedgerEntry> window) {
        Map<String, RecurringFlow> groups = new LinkedHashMap<>();
        TreeSet<YearMonth> months = new TreeSet<>();
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (LedgerEntry entry : window) {
            if (entry.bookingDate() == null) {
                continue;
            }
            LocalDate date = entry.bookingDate().atZone(UTC).toLocalDate();
            months.add(YearMonth.from(date));
            BigDecimal amount = entry.amount() == null ? BigDecimal.ZERO : entry.amount().abs();
            if (entry.direction() == Direction.CREDIT) {
                totalCredit = totalCredit.add(amount);
            }
            String key = entry.direction() + "|" + entry.category().name() + "|"
                    + (entry.descriptionCleansed() == null ? ""
                            : entry.descriptionCleansed().strip().toLowerCase(Locale.ROOT));
            groups.computeIfAbsent(key, k -> new RecurringFlow(entry.direction())).add(date, amount);
        }

        BigDecimal recurringIncome = BigDecimal.ZERO;
        BigDecimal essentials = BigDecimal.ZERO;
        for (RecurringFlow flow : groups.values()) {
            if (!flow.isRecurringMonthly()) {
                continue;
            }
            if (flow.direction == Direction.CREDIT) {
                recurringIncome = recurringIncome.add(flow.typicalAmount());
            } else {
                essentials = essentials.add(flow.typicalAmount());
            }
        }

        // Fall back to mean monthly credit when no clean recurring inflow is detected (irregular income).
        BigDecimal income = recurringIncome.signum() > 0
                ? recurringIncome
                : totalCredit.divide(BigDecimal.valueOf(Math.max(1, months.size())), 2, RoundingMode.HALF_UP);
        return new LoanTelemetry(income, essentials);
    }

    /**
     * One candidate recurring group: the dates it occurred on and its total amount, plus — derived on
     * demand — whether those dates form an approximately monthly cadence. Mirrors the forecast engine's
     * recurrence test (DESIGN §5.2) so essentials/income here agree with how the forecast schedules flows.
     */
    private static final class RecurringFlow {

        private final Direction direction;
        private final List<LocalDate> dates = new ArrayList<>();
        private BigDecimal total = BigDecimal.ZERO;

        RecurringFlow(Direction direction) {
            this.direction = direction;
        }

        void add(LocalDate date, BigDecimal amount) {
            dates.add(date);
            total = total.add(amount);
        }

        /** Mean amount per occurrence — the flow's typical monthly magnitude. */
        BigDecimal typicalAmount() {
            return total.divide(BigDecimal.valueOf(dates.size()), 2, RoundingMode.HALF_UP);
        }

        /**
         * True when the occurrences repeat on a roughly monthly cadence: at least
         * {@value #MIN_RECURRING_OCCURRENCES} of them, with a median consecutive-gap within
         * {@value #CADENCE_TOLERANCE_DAYS} days of a {@value #MONTHLY_DAYS}-day month. Frequent bursty spend
         * (many small gaps) has a tiny median gap and is correctly rejected as discretionary.
         */
        boolean isRecurringMonthly() {
            if (dates.size() < MIN_RECURRING_OCCURRENCES) {
                return false;
            }
            List<LocalDate> sorted = new ArrayList<>(dates);
            sorted.sort(LocalDate::compareTo);
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < sorted.size(); i++) {
                gaps.add(ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i)));
            }
            gaps.sort(Long::compareTo);
            long medianGap = gaps.get(gaps.size() / 2);
            return Math.abs(medianGap - MONTHLY_DAYS) <= CADENCE_TOLERANCE_DAYS;
        }
    }
}
