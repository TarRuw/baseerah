package com.baseerah.forecast;

import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.baseerah.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Default {@link ForecastEngine}: a pure-Java heuristic cash-flow projection (DESIGN.md §5.2).
 *
 * <p>Structured as a functional core inside an imperative shell — the same split as
 * {@code StressScoreCalculator}/{@code StressScoreSnapshotWriter}. {@link #project} (the shell) reads the
 * clock and the database to assemble the trailing window, then hands off to {@link #forecast} (the core),
 * which is pure and deterministic over its inputs so the persona unit test needs no Spring or Postgres.
 *
 * <p>The core: (1) start from the client's latest {@code closing_balance}; (2) group transactions by
 * {@code direction + category + description_cleansed} and classify each group as <em>recurring monthly</em>
 * when its occurrences fall on an approximately monthly cadence — separating recurring inflows (e.g. salary)
 * from recurring outflows (rent, subscriptions, instalments); (3) treat every non-recurring debit as
 * discretionary and average it into a daily burn; (4) walk day-by-day over the horizon, applying each
 * recurring flow on its due day-of-month and subtracting the burn, recording the balance, the first date it
 * crosses zero, and the running minimum. Everything is derived from the seeded transactions — no prototype
 * constants (Global Rules).
 *
 * <p><strong>Design decisions (see the step handoff):</strong> the trailing window is
 * {@value #WINDOW_DAYS} days; a group counts as recurring only when it has at least
 * {@value #MIN_RECURRING_OCCURRENCES} occurrences whose median inter-occurrence gap is within
 * {@value #CADENCE_TOLERANCE_DAYS} days of a {@value #MONTHLY_DAYS}-day month. That deliberately keeps
 * clean ~monthly items (salary, rent) as scheduled flows while letting frequent, bursty spend (groceries,
 * ride-hailing) fall through to discretionary burn.
 */
@Service
public class HeuristicForecast implements ForecastEngine {

    /** Trailing window (days) the shell loads from the database and feeds to the core — DESIGN §5.2. */
    public static final int WINDOW_DAYS = 90;

    private static final int MONTHLY_DAYS = 30;
    /** A group is recurring-monthly if its median occurrence gap is {@code 30 ± this} days. */
    private static final int CADENCE_TOLERANCE_DAYS = 7;
    /** A cadence needs at least this many occurrences (i.e. at least one gap) to be established. */
    private static final int MIN_RECURRING_OCCURRENCES = 2;

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Autowired
    public HeuristicForecast(TransactionRepository transactionRepository) {
        this(transactionRepository, Clock.systemUTC());
    }

    HeuristicForecast(TransactionRepository transactionRepository, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Override
    public ForecastResult project(UUID clientId, int horizonDays) {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        List<Transaction> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(clientId, from, to);
        return forecast(window, today, horizonDays);
    }

    /**
     * Pure projection over an explicit window. The caller supplies exactly the transactions to model and
     * the {@code today} to project from; this method touches neither the clock nor the database.
     *
     * @param window      a client's transactions (any that lack a booking date are ignored)
     * @param today       the day the projection starts from (inclusive)
     * @param horizonDays days to project; the series covers {@code [today, today + horizonDays]} inclusive
     */
    ForecastResult forecast(List<Transaction> window, LocalDate today, int horizonDays) {
        BigDecimal startBalance = latestClosingBalance(window);

        // Group transactions by direction + category + cleansed description; track the window's date span.
        Map<String, RecurrenceGroup> groups = new LinkedHashMap<>();
        LocalDate earliest = null;
        LocalDate latest = null;
        for (Transaction tx : window) {
            if (tx.getBookingDate() == null) {
                continue;
            }
            LocalDate date = tx.getBookingDate().atZone(UTC).toLocalDate();
            earliest = (earliest == null || date.isBefore(earliest)) ? date : earliest;
            latest = (latest == null || date.isAfter(latest)) ? date : latest;
            String key = tx.getDirection() + "|" + tx.resolveCategory().name() + "|"
                    + (tx.getDescriptionCleansed() == null ? ""
                            : tx.getDescriptionCleansed().strip().toLowerCase(Locale.ROOT));
            BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount().abs();
            groups.computeIfAbsent(key, k -> new RecurrenceGroup(tx.getDirection())).add(date, amount);
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

    /** The {@code closing_balance} of the most recent dated transaction, or zero when the window is empty. */
    private static BigDecimal latestClosingBalance(List<Transaction> window) {
        Transaction latest = null;
        for (Transaction tx : window) {
            if (tx.getBookingDate() == null) {
                continue;
            }
            if (latest == null || tx.getBookingDate().isAfter(latest.getBookingDate())) {
                latest = tx;
            }
        }
        return (latest != null && latest.getClosingBalance() != null)
                ? latest.getClosingBalance() : BigDecimal.ZERO;
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
