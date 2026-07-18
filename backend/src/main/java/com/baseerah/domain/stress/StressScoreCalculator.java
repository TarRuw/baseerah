package com.baseerah.domain.stress;

import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.domain.expense.DeclaredExpenseObligations;
import com.baseerah.domain.kernel.Direction;
import com.baseerah.domain.kernel.LedgerEntry;
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
import java.util.UUID;

/**
 * Computes the Financial Stress Score (DESIGN.md §5.1) from a client's ledger entries over a trailing
 * window. Pure and deterministic over its input list — it never touches the clock or the database, so
 * unit tests are stable (the clock/DB live in the application shell {@code StressScoreSnapshotWriter}).
 *
 * <p>The pipeline is: (1) crunch the raw telemetry into three interpretable metrics — burn rate, income
 * regularity, and obligation/buffer structure; (2) normalise each into a {@code [0,1]} healthiness
 * sub-score via a monotonic curve; (3) combine with the §5.1 weights into a 0–100 score and a
 * {@link Zone}. Everything is computed from the seeded transactions — no prototype constants (Global
 * Rules). Categories are read off each {@link LedgerEntry} (data-driven, enum-with-fallback resolved when
 * the entry is mapped from persistence), so nothing hardcodes a fixed category list.
 *
 * <p>Framework-free by design (domain layer): the application service loads {@code Transaction} rows,
 * maps them to {@link LedgerEntry} records, and calls {@link #calculate(List)} — the calculator sees only
 * domain types.
 *
 * <p><strong>Step 2.1 open decision — normalisation curves.</strong> The three {@code *Score} methods at
 * the bottom map each raw metric into {@code [0,1]}. DESIGN §5.1 deliberately leaves their exact shape to
 * the implementing engineer; they are the one place in this file where the choice changes where a persona
 * lands on the gauge. Each has full guidance in its Javadoc.
 */
public class StressScoreCalculator {

    /** Default trailing window (days) the snapshot writer queries and feeds in — DESIGN §5.1. */
    public static final int DEFAULT_WINDOW_DAYS = 90;

    // §5.1 combination weights: velocity 0.4, consistency 0.3, liability 0.3.
    private static final double W_VELOCITY = 0.4;
    private static final double W_CONSISTENCY = 0.3;
    private static final double W_LIABILITY = 0.3;

    /**
     * Score a client's ledger window with <strong>no declared expenses</strong> — the feed-only view.
     * Convenience overload equivalent to {@code calculate(window, List.of())}; kept so callers and tests that
     * predate Phase 11 stay bit-identical (a client who has declared nothing scores exactly as before).
     */
    public StressScoreResult calculate(List<LedgerEntry> window) {
        return calculate(window, List.of());
    }

    /**
     * Score the given window of a single client's ledger entries, widening the obligation picture with the
     * client's <strong>declared periodic expenses</strong> (Phase 11 / Step 11.3). The caller supplies exactly
     * the window to score (e.g. the trailing {@link #DEFAULT_WINDOW_DAYS} days) and the client's active
     * declared expenses; this method derives every metric — including the current buffer (latest closing
     * balance) — from that list.
     *
     * <p>Declared expenses are read <em>additively</em>: their {@link DeclaredExpenseObligations#monthlyTotal
     * monthly total} is added to the recurring obligations inferred from the feed before {@code obligationShare}
     * is computed, so declaring an expense the SAMA feed cannot see raises the obligation load and lowers the
     * score. The declared total is folded in via the shared {@link DeclaredExpenseObligations} helper — the
     * <em>same</em> helper the bank's {@code UnderwritingRule} calls — so the two cannot drift (enforced by the
     * parity test). Passing an empty list reproduces the pre-Phase-11 feed-only score exactly.
     */
    public StressScoreResult calculate(List<LedgerEntry> window, List<DeclaredExpense> declared) {
        // --- 1. Aggregate raw telemetry --------------------------------------------------------------
        BigDecimal debitSum = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;
        Set<YearMonth> months = new TreeSet<>();
        Map<YearMonth, BigDecimal> incomeByMonth = new LinkedHashMap<>();

        // The client's buffer is the sum of each account's own latest closing balance. Tracking only the
        // single newest entry of the merged window would silently report one arbitrary account's balance
        // as the whole client's (whichever transacted last) once a client holds more than one account.
        Map<UUID, LedgerEntry> latestByAccount = new LinkedHashMap<>();
        for (LedgerEntry tx : window) {
            YearMonth ym = YearMonth.from(tx.bookingDate().atZone(ZoneOffset.UTC).toLocalDate());
            months.add(ym);
            BigDecimal amount = tx.amount() == null ? BigDecimal.ZERO : tx.amount().abs();
            latestByAccount.merge(tx.accountId(), tx,
                    (prev, cur) -> cur.bookingDate().isAfter(prev.bookingDate()) ? cur : prev);
            if (tx.direction() == Direction.CREDIT) {
                creditSum = creditSum.add(amount);
                incomeByMonth.merge(ym, amount, BigDecimal::add);
            } else {
                debitSum = debitSum.add(amount);
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

        // (c) Liability structure: recurring-obligation share of income + runway buffer. The recurring load is
        //     feed-inferred groups + the declared-expense monthly total (Step 11.3) — see the shared accessor.
        double recurringMonthly = recurringMonthlyObligations(window, declared);
        double obligationShare = monthlyIncome > 0 ? recurringMonthly / monthlyIncome : 1.0;
        double latestBalance = sumLatestClosingBalances(latestByAccount.values());
        double bufferMonths = monthlyExpense > 0 ? latestBalance / monthlyExpense : Double.MAX_VALUE;

        // --- 3. Normalise into [0,1] sub-scores, then combine ----------------------------------------
        double velocity = hasIncome ? clamp01(velocityScore(burnRate)) : 0.0;
        double consistency = clamp01(consistencyScore(incomeCv, cadenceCoverage));
        double liability = clamp01(liabilityScore(obligationShare, bufferMonths));

        double weighted = W_VELOCITY * velocity + W_CONSISTENCY * consistency + W_LIABILITY * liability;
        int score = (int) Math.max(0, Math.min(100, Math.round(100.0 * weighted)));

        return new StressScoreResult(score, Zone.forScore(score), velocity, consistency, liability);
    }

    /**
     * The client's total recurring <em>monthly</em> obligation (SAR): the feed-inferred recurring debit load
     * plus the declared-expense monthly total. This is the exact figure {@link #calculate} folds into
     * {@code obligationShare}, exposed so the {@code UnderwritingStressParityTest} can assert the stress
     * liability view and the bank {@code UnderwritingRule} DTI view agree for the same inputs — the invariant
     * this step makes structural rather than comment-protected.
     *
     * <p>The declared contribution comes from the shared {@link DeclaredExpenseObligations#monthlyTotal}, the
     * same helper the underwriting rule uses, so the declared part cannot drift between the two. The
     * feed-inferred part still deliberately mirrors {@code UnderwritingRule.deriveTelemetry} (§5.1).
     *
     * @param window   the client's ledger window
     * @param declared the client's declared expenses (empty for the feed-only view)
     * @return recurring feed obligations + active declared monthly total, as a {@code double}
     */
    public double recurringMonthlyObligations(List<LedgerEntry> window, List<DeclaredExpense> declared) {
        return inferredRecurringMonthly(window)
                + DeclaredExpenseObligations.monthlyTotal(declared).doubleValue();
    }

    /**
     * The feed-inferred recurring monthly obligation: debits grouped by {@code category + cleansed
     * description}, keeping only groups seen in at least {@code max(2, ceil(months/2))} distinct months, each
     * averaged over the window's month count. Byte-for-byte the §5.1 rule that {@code UnderwritingRule} also
     * duplicates — extracted here so {@link #calculate} and {@link #recurringMonthlyObligations} share one
     * copy (declared expenses are added on top, not here).
     */
    private static double inferredRecurringMonthly(List<LedgerEntry> window) {
        Set<YearMonth> months = new TreeSet<>();
        Map<String, RecurringGroup> debitGroups = new LinkedHashMap<>();
        for (LedgerEntry tx : window) {
            YearMonth ym = YearMonth.from(tx.bookingDate().atZone(ZoneOffset.UTC).toLocalDate());
            months.add(ym);
            if (tx.direction() != Direction.CREDIT) {
                BigDecimal amount = tx.amount() == null ? BigDecimal.ZERO : tx.amount().abs();
                String key = tx.category().name() + "|"
                        + (tx.descriptionCleansed() == null ? "" : tx.descriptionCleansed().strip()
                                .toLowerCase(Locale.ROOT));
                debitGroups.computeIfAbsent(key, k -> new RecurringGroup()).add(ym, amount);
            }
        }
        int monthCount = Math.max(1, months.size());
        int recurringThreshold = Math.max(2, (int) Math.ceil(monthCount / 2.0));
        double recurringMonthly = 0.0;
        for (RecurringGroup g : debitGroups.values()) {
            if (g.months.size() >= recurringThreshold) {
                recurringMonthly += g.total.doubleValue() / monthCount;
            }
        }
        return recurringMonthly;
    }

    // --- Shared helpers (implemented — not part of the open decision) ---------------------------------

    /**
     * The client's buffer: each account's own latest closing balance, summed. Entries whose balance is
     * unknown contribute nothing rather than zeroing the total.
     */
    private static double sumLatestClosingBalances(Collection<LedgerEntry> latestPerAccount) {
        BigDecimal total = BigDecimal.ZERO;
        for (LedgerEntry entry : latestPerAccount) {
            if (entry.closingBalance() != null) {
                total = total.add(entry.closingBalance());
            }
        }
        return total.doubleValue();
    }

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
