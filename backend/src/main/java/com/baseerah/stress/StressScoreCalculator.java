package com.baseerah.stress;

import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Computes the Financial Stress Score (DESIGN.md §5.1) from a client's transactions over a trailing
 * window. Pure and deterministic over its input list — it never touches the clock or the database, so
 * unit tests are stable (the clock/DB live in {@link StressScoreSnapshotWriter}).
 *
 * <p>The pipeline is: (1) crunch the raw telemetry into three interpretable metrics — burn rate, income
 * regularity, and obligation/buffer structure; (2) normalise each into a {@code [0,1]} healthiness
 * sub-score via a monotonic curve; (3) combine with the §5.1 weights into a 0–100 score and a
 * {@link Zone}. Everything is computed from the seeded transactions — no prototype constants (Global
 * Rules). Categories are read via {@link Transaction#resolveCategory()} (data-driven, enum-with-fallback),
 * so nothing hardcodes a fixed category list.
 *
 * <p><strong>Step 2.1 open decision — normalisation curves.</strong> The three {@code *Score} methods at
 * the bottom map each raw metric into {@code [0,1]}. DESIGN §5.1 deliberately leaves their exact shape to
 * the implementing engineer; they are the one place in this file where the choice changes where a persona
 * lands on the gauge. Each has full guidance in its Javadoc.
 */
@Service
public class StressScoreCalculator {

    /** Default trailing window (days) the snapshot writer queries and feeds in — DESIGN §5.1. */
    public static final int DEFAULT_WINDOW_DAYS = 90;

    // §5.1 combination weights: velocity 0.4, consistency 0.3, liability 0.3.
    private static final double W_VELOCITY = 0.4;
    private static final double W_CONSISTENCY = 0.3;
    private static final double W_LIABILITY = 0.3;

    /**
     * Score the given window of a single client's transactions. The caller is responsible for supplying
     * exactly the window to score (e.g. the trailing {@link #DEFAULT_WINDOW_DAYS} days); this method
     * derives every metric — including the current buffer (latest closing balance) — from that list.
     */
    public StressScoreResult calculate(List<Transaction> window) {
        // --- 1. Aggregate raw telemetry --------------------------------------------------------------
        BigDecimal debitSum = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;
        Set<YearMonth> months = new TreeSet<>();
        Map<YearMonth, BigDecimal> incomeByMonth = new LinkedHashMap<>();
        // Recurring-obligation detection: group debits by (category + cleansed description) and track the
        // distinct months each group appears in. A group seen in most months of the window is "recurring".
        Map<String, RecurringGroup> debitGroups = new LinkedHashMap<>();

        Transaction latest = null;
        for (Transaction tx : window) {
            YearMonth ym = YearMonth.from(tx.getBookingDate().atZone(ZoneOffset.UTC).toLocalDate());
            months.add(ym);
            BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount().abs();
            if (latest == null || tx.getBookingDate().isAfter(latest.getBookingDate())) {
                latest = tx;
            }
            if (tx.getDirection() == Direction.CREDIT) {
                creditSum = creditSum.add(amount);
                incomeByMonth.merge(ym, amount, BigDecimal::add);
            } else {
                debitSum = debitSum.add(amount);
                String key = tx.resolveCategory().name() + "|"
                        + (tx.getDescriptionCleansed() == null ? "" : tx.getDescriptionCleansed().strip()
                                .toLowerCase(Locale.ROOT));
                debitGroups.computeIfAbsent(key, k -> new RecurringGroup()).add(ym, amount);
            }
        }

        int monthCount = Math.max(1, months.size());
        double monthlyIncome = creditSum.doubleValue() / monthCount;
        double monthlyExpense = debitSum.doubleValue() / monthCount;

        // --- 2. Derive the three interpretable metrics -----------------------------------------------
        // (a) Spending velocity: burn rate = debits / credits over the window. Lower is healthier.
        //     No income at all is maximally unhealthy, so short-circuit to the worst velocity sub-score.
        boolean hasIncome = creditSum.signum() > 0;
        double burnRate = hasIncome ? debitSum.doubleValue() / creditSum.doubleValue() : Double.NaN;

        // (b) Income consistency: coefficient of variation of monthly income (steadier = lower CV), plus
        //     cadence coverage = share of window months that actually received income (regular = closer to 1).
        double incomeCv = coefficientOfVariation(incomeByMonth.values());
        double cadenceCoverage = (double) incomeByMonth.size() / monthCount;

        // (c) Liability structure: recurring-obligation share of income + runway buffer.
        int recurringThreshold = Math.max(2, (int) Math.ceil(monthCount / 2.0));
        double recurringMonthly = 0.0;
        for (RecurringGroup g : debitGroups.values()) {
            if (g.months.size() >= recurringThreshold) {
                recurringMonthly += g.total.doubleValue() / monthCount;
            }
        }
        double obligationShare = monthlyIncome > 0 ? recurringMonthly / monthlyIncome : 1.0;
        double latestBalance = latest != null && latest.getClosingBalance() != null
                ? latest.getClosingBalance().doubleValue() : 0.0;
        double bufferMonths = monthlyExpense > 0 ? latestBalance / monthlyExpense : Double.MAX_VALUE;

        // --- 3. Normalise into [0,1] sub-scores, then combine ----------------------------------------
        double velocity = hasIncome ? clamp01(velocityScore(burnRate)) : 0.0;
        double consistency = clamp01(consistencyScore(incomeCv, cadenceCoverage));
        double liability = clamp01(liabilityScore(obligationShare, bufferMonths));

        double weighted = W_VELOCITY * velocity + W_CONSISTENCY * consistency + W_LIABILITY * liability;
        int score = (int) Math.max(0, Math.min(100, Math.round(100.0 * weighted)));

        return new StressScoreResult(score, Zone.forScore(score), velocity, consistency, liability);
    }

    // --- Shared helpers (implemented — not part of the open decision) ---------------------------------

    /** Population coefficient of variation (stddev / mean) of a set of monthly totals; 0 when < 2 samples. */
    private static double coefficientOfVariation(Collection<BigDecimal> monthlyTotals) {
        List<Double> xs = new ArrayList<>();
        for (BigDecimal v : monthlyTotals) {
            xs.add(v.doubleValue());
        }
        if (xs.size() < 2) {
            return 0.0;
        }
        double mean = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (mean == 0.0) {
            return 0.0;
        }
        double variance = xs.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum() / xs.size();
        return Math.sqrt(variance) / mean;
    }

    /** Clamp any value into {@code [0,1]} — applied to every curve's output so a curve can't escape range. */
    static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
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

    // =================================================================================================
    // Normalisation curves — STEP 2.1 ENGINEER'S DECISION (see class Javadoc + step-02-01 handoff).
    // Each maps a raw metric to a healthiness sub-score in [0,1] (higher = healthier). Output is passed
    // through clamp01, so these need not clamp themselves, but a monotonic shape is expected. Tuning
    // these moves where each persona lands on the gauge — the calculator test pins the required ordering
    // (client_001_family must score higher than client_003_freelancer) and the [0,100] range.
    // Chosen shapes are transparent piecewise-linear ramps (documented per method); revisit here to retune.
    // =================================================================================================

    /**
     * Spending velocity → healthiness. {@code burnRate = debits/credits} over the window; lower is
     * healthier. Suggested anchors: spending ≤ ~half of income → ~1.0; spending ≥ income (burnRate ≥ ~1.5)
     * → ~0.0, decreasing monotonically between. (Reference shape: {@code 1 - (burnRate - 0.5) / 1.0}.)
     */
    private static double velocityScore(double burnRate) {
        // Linear: spending half of income (0.5) → 1.0; spending 50% over income (1.5) → 0.0.
        return 1.0 - (burnRate - 0.5) / 1.0;
    }

    /**
     * Income consistency → healthiness. {@code incomeCv} is the coefficient of variation of monthly
     * income (0 = perfectly steady, grows as income gets erratic); {@code cadenceCoverage} in {@code [0,1]}
     * is the share of window months that actually received income (1 = income every month). Steadier and
     * more regular is healthier. (Reference shape: {@code (1 - incomeCv) * cadenceCoverage}.)
     */
    private static double consistencyScore(double incomeCv, double cadenceCoverage) {
        // Steadiness (1 - CV, floored at 0) scaled by how many window months actually saw income.
        return clamp01(1.0 - incomeCv) * cadenceCoverage;
    }

    /**
     * Liability structure → healthiness. {@code obligationShare} in ~{@code [0,∞)} is recurring monthly
     * obligations as a fraction of monthly income (lower is healthier; ≥ ~0.6 is heavy). {@code bufferMonths}
     * is months of runway = latest balance / monthly expense (higher is healthier; ~3+ is comfortable).
     * Combine both — e.g. {@code 0.5 * (1 - obligationShare/0.6) + 0.5 * (bufferMonths/3)}.
     */
    private static double liabilityScore(double obligationShare, double bufferMonths) {
        // Half from obligation load (≥0.6 of income → 0), half from runway (≥3 months → 1).
        double obligationComponent = clamp01(1.0 - obligationShare / 0.6);
        double bufferComponent = clamp01(bufferMonths / 3.0);
        return 0.5 * obligationComponent + 0.5 * bufferComponent;
    }
}
