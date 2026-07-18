package com.baseerah.domain.forecast;

import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.domain.kernel.Direction;
import com.baseerah.domain.kernel.LedgerEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The pure cash-flow projection core (DESIGN.md §5.2) — extracted from the former {@code HeuristicForecast}
 * so the algorithm is framework-free, mirroring the {@code StressScoreCalculator} functional-core split.
 * The imperative shell ({@code HeuristicForecastEngine}) reads the clock and the database to assemble the
 * trailing window and maps it to {@link LedgerEntry}s; {@link #project} is deterministic over its inputs,
 * so the persona unit test needs no Spring or Postgres.
 *
 * <p>The core: (1) start from the client's latest {@code closingBalance}; (2) group entries by
 * {@code direction + category + descriptionCleansed} and classify each group as <em>recurring monthly</em>
 * when its occurrences fall on an approximately monthly cadence — separating recurring inflows (e.g. salary)
 * from recurring outflows (rent, subscriptions, instalments); (3) treat every non-recurring debit as
 * discretionary and average it into a daily burn; (4) walk day-by-day over the horizon, applying each
 * recurring flow on its due day-of-month and subtracting the burn, recording the balance, the first date it
 * crosses zero, and the running minimum. Everything is derived from the seeded telemetry — no prototype
 * constants (Global Rules).
 *
 * <p><strong>Design decisions (see the step handoff):</strong> a group counts as recurring only when it has
 * at least {@value #MIN_RECURRING_OCCURRENCES} occurrences whose median inter-occurrence gap is within
 * {@value #CADENCE_TOLERANCE_DAYS} days of a {@value #MONTHLY_DAYS}-day month. That deliberately keeps clean
 * ~monthly items (salary, rent) as scheduled flows while letting frequent, bursty spend (groceries,
 * ride-hailing) fall through to discretionary burn. The trailing-window size the shell loads is the shell's
 * concern; this core models exactly the window it is given.
 */
public final class BalanceProjection {

    private static final int MONTHLY_DAYS = 30;
    /** A group is recurring-monthly if its median occurrence gap is {@code 30 ± this} days. */
    private static final int CADENCE_TOLERANCE_DAYS = 7;
    /** A cadence needs at least this many occurrences (i.e. at least one gap) to be established. */
    private static final int MIN_RECURRING_OCCURRENCES = 2;

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private BalanceProjection() {
    }

    /**
     * Pure projection over an explicit window. The caller supplies exactly the ledger entries to model and
     * the {@code today} to project from; this method touches neither a clock nor a database.
     *
     * @param window      a client's ledger entries (any that lack a booking date are ignored)
     * @param today       the day the projection starts from (inclusive)
     * @param horizonDays days to project; the series covers {@code [today, today + horizonDays]} inclusive
     * @return the projected points, the first deficit date (nullable), and the minimum projected balance
     */
    public static ForecastResult project(List<LedgerEntry> window, LocalDate today, int horizonDays) {
        return project(window, List.of(), today, horizonDays);
    }

    /**
     * Pure projection over an explicit window, additionally scheduling the client's <strong>declared periodic
     * expenses</strong> (Phase 11 / Step 11.3). Each <em>active</em> declared expense is emitted directly as a
     * recurring monthly outflow on its {@code dayOfMonth} at its {@code amount} — it needs <em>no</em>
     * inference: a declared expense already <em>is</em> a stated recurrence, so it bypasses the feed's
     * median-gap cadence detection entirely and never lands in discretionary daily burn. This is the one flow
     * that is recognised without a matching {@code descriptionCleansed}, unlike every feed-derived recurrence.
     * Inactive (soft-deleted) declared expenses are skipped, so a deactivated expense has no effect. Passing an
     * empty list reproduces the feed-only projection exactly.
     *
     * @param window      a client's ledger entries (any that lack a booking date are ignored)
     * @param declared    the client's declared expenses (only active ones are scheduled; may be empty)
     * @param today       the day the projection starts from (inclusive)
     * @param horizonDays days to project; the series covers {@code [today, today + horizonDays]} inclusive
     * @return the projected points, the first deficit date (nullable), and the minimum projected balance
     */
    public static ForecastResult project(List<LedgerEntry> window, List<DeclaredExpense> declared,
            LocalDate today, int horizonDays) {
        BigDecimal startBalance = latestClosingBalance(window);

        // Group entries by direction + category + cleansed description; track the window's date span.
        Map<String, RecurrenceGroup> groups = new LinkedHashMap<>();
        LocalDate earliest = null;
        LocalDate latest = null;
        for (LedgerEntry entry : window) {
            if (entry.bookingDate() == null) {
                continue;
            }
            LocalDate date = entry.bookingDate().atZone(UTC).toLocalDate();
            earliest = (earliest == null || date.isBefore(earliest)) ? date : earliest;
            latest = (latest == null || date.isAfter(latest)) ? date : latest;
            String key = entry.direction() + "|" + entry.category().name() + "|"
                    + (entry.descriptionCleansed() == null ? ""
                            : entry.descriptionCleansed().strip().toLowerCase(Locale.ROOT));
            BigDecimal amount = entry.amount() == null ? BigDecimal.ZERO : entry.amount().abs();
            groups.computeIfAbsent(key, k -> new RecurrenceGroup(entry.direction())).add(date, amount);
        }

        // Split groups into scheduled recurring flows vs discretionary (non-recurring debit) spend.
        List<ScheduledFlow> inflows = new ArrayList<>();
        List<ScheduledFlow> outflows = new ArrayList<>();
        BigDecimal discretionaryDebit = BigDecimal.ZERO;
        for (RecurrenceGroup group : groups.values()) {
            if (group.isRecurringMonthly()) {
                ScheduledFlow flow = new ScheduledFlow(group.typicalDayOfMonth(), group.typicalAmount());
                (group.direction == Direction.CREDIT ? inflows : outflows).add(flow);
            } else if (group.direction == Direction.DEBIT) {
                discretionaryDebit = discretionaryDebit.add(group.total);
            }
        }

        // Declared periodic expenses (Step 11.3): each active one is a stated monthly recurrence, so it maps
        // straight onto a scheduled outflow — no cadence inference, never discretionary burn.
        for (DeclaredExpense expense : declared) {
            if (expense != null && expense.active() && expense.amount() != null) {
                outflows.add(new ScheduledFlow(expense.dayOfMonth(), expense.amount().abs()));
            }
        }

        // Discretionary daily burn = non-recurring debits averaged over the observed window span.
        long windowDays = (earliest == null) ? 1 : Math.max(1, ChronoUnit.DAYS.between(earliest, latest) + 1);
        BigDecimal dailyBurn = discretionaryDebit.divide(BigDecimal.valueOf(windowDays), 2, RoundingMode.HALF_UP);

        // Walk the horizon day by day.
        List<ForecastPoint> points = new ArrayList<>();
        BigDecimal balance = startBalance;
        LocalDate deficitDate = null;
        BigDecimal minBalance = startBalance;
        for (int i = 0; i <= horizonDays; i++) {
            LocalDate day = today.plusDays(i);
            int dayOfMonth = day.getDayOfMonth();
            int monthLength = day.lengthOfMonth();
            for (ScheduledFlow flow : inflows) {
                if (flow.dueOn(dayOfMonth, monthLength)) {
                    balance = balance.add(flow.amount);
                }
            }
            for (ScheduledFlow flow : outflows) {
                if (flow.dueOn(dayOfMonth, monthLength)) {
                    balance = balance.subtract(flow.amount);
                }
            }
            balance = balance.subtract(dailyBurn);
            points.add(new ForecastPoint(day, balance));
            if (deficitDate == null && balance.signum() < 0) {
                deficitDate = day;
            }
            if (balance.compareTo(minBalance) < 0) {
                minBalance = balance;
            }
        }
        return new ForecastResult(points, deficitDate, minBalance);
    }

    /** The {@code closingBalance} of the most recent dated entry, or zero when the window is empty. */
    /**
     * The starting balance: each account's own latest closing balance, summed. A client may hold several
     * accounts and {@code closingBalance} is scoped to one of them, so taking the single newest entry of the
     * merged window would start the projection from whichever account transacted last — understating (or
     * overstating) the client's money and moving the deficit date with it.
     */
    private static BigDecimal latestClosingBalance(List<LedgerEntry> window) {
        Map<UUID, LedgerEntry> latestByAccount = new LinkedHashMap<>();
        for (LedgerEntry entry : window) {
            if (entry.bookingDate() == null) {
                continue;
            }
            latestByAccount.merge(entry.accountId(), entry,
                    (prev, cur) -> cur.bookingDate().isAfter(prev.bookingDate()) ? cur : prev);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (LedgerEntry entry : latestByAccount.values()) {
            if (entry.closingBalance() != null) {
                total = total.add(entry.closingBalance());
            }
        }
        return total;
    }

    /** A recurring flow reduced to its typical day-of-month and amount, applied once per projected month. */
    private record ScheduledFlow(int dayOfMonth, BigDecimal amount) {

        /** Due when the projected day matches the typical day, clamped into short months (e.g. 31 → 30/28). */
        boolean dueOn(int projectedDayOfMonth, int monthLength) {
            return projectedDayOfMonth == Math.min(dayOfMonth, monthLength);
        }
    }

    /**
     * Accumulates one candidate recurring group: the dates it occurred on, its total spend, and — derived
     * on demand — whether those dates form an approximately monthly cadence.
     */
    private static final class RecurrenceGroup {

        private final Direction direction;
        private final List<LocalDate> dates = new ArrayList<>();
        private BigDecimal total = BigDecimal.ZERO;

        RecurrenceGroup(Direction direction) {
            this.direction = direction;
        }

        void add(LocalDate date, BigDecimal amount) {
            dates.add(date);
            total = total.add(amount);
        }

        /** Mean amount per occurrence — the flow's typical magnitude. */
        BigDecimal typicalAmount() {
            return total.divide(BigDecimal.valueOf(dates.size()), 2, RoundingMode.HALF_UP);
        }

        /** Median day-of-month across occurrences — the flow's typical due day. */
        int typicalDayOfMonth() {
            List<Integer> days = new ArrayList<>();
            for (LocalDate d : dates) {
                days.add(d.getDayOfMonth());
            }
            days.sort(Integer::compareTo);
            return days.get(days.size() / 2);
        }

        /**
         * True when the occurrences repeat on a roughly monthly cadence: at least
         * {@link #MIN_RECURRING_OCCURRENCES} of them, with a median consecutive-gap within
         * {@link #CADENCE_TOLERANCE_DAYS} of a {@link #MONTHLY_DAYS}-day month. Frequent bursty spend
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
