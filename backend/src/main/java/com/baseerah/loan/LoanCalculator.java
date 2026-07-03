package com.baseerah.loan;

import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import com.baseerah.stress.StressScoreService;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.baseerah.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Interactive loan affordability (FR-05, DESIGN.md §5.3) — mirrors the prototype's math exactly. Split as a
 * functional core / imperative shell, the same shape as {@link com.baseerah.forecast.HeuristicForecast} and
 * {@link com.baseerah.stress.StressScoreCalculator}: {@link #simulate} (the shell) resolves the client,
 * reads the trailing transaction window and the current stress score, and derives income/essentials from
 * that telemetry; {@link #compute} (the core) is pure and deterministic over its explicit inputs, so the
 * §5.3 arithmetic is unit-tested with no Spring and no database.
 *
 * <p><strong>No prototype constants.</strong> The prototype hardcodes the family persona's
 * {@code income=18500 / essentials=14200 / surplus=4300} as demo stand-ins; the real build derives income
 * and essentials from the client's own seeded transactions (Global Rule; DESIGN §5.3).
 *
 * <p><strong>Colours.</strong> {@code verdict} and {@code dti} bands are reported as DESIGN §8 palette hexes
 * — green {@value #GREEN}, orange {@value #ORANGE}, red {@value #RED} — kept identical to the Step 3.5
 * Flutter theme tokens. The DTI banding thresholds ({@code <70% / <90%}) come from the prototype (the
 * §6 endpoint contract did not pin them); the prototype's pastel hexes are remapped to this canonical palette.
 */
@Service
public class LoanCalculator {

    /** Trailing window (days) the shell loads to derive income/essentials — matches the §5.1/§5.2 windows. */
    public static final int WINDOW_DAYS = 90;

    // DESIGN §8 gauge/band palette — must stay identical to the Step 3.5 Flutter theme tokens.
    static final String GREEN = "#1D9E63";
    static final String ORANGE = "#E5A63A";
    static final String RED = "#E0574F";

    // Verdict text (DESIGN §5.3).
    static final String VERDICT_COMFORTABLE = "Comfortably affordable";
    static final String VERDICT_STRAINS = "Strains liquidity";
    static final String VERDICT_NOT_AFFORDABLE = "Not affordable";

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

    private final ClientService clientService;
    private final TransactionRepository transactionRepository;
    private final StressScoreService stressScoreService;
    private final Clock clock;

    @Autowired
    public LoanCalculator(ClientService clientService, TransactionRepository transactionRepository,
            StressScoreService stressScoreService) {
        this(clientService, transactionRepository, stressScoreService, Clock.systemUTC());
    }

    LoanCalculator(ClientService clientService, TransactionRepository transactionRepository,
            StressScoreService stressScoreService, Clock clock) {
        this.clientService = clientService;
        this.transactionRepository = transactionRepository;
        this.stressScoreService = stressScoreService;
        this.clock = clock;
    }

    /**
     * Simulate a loan for a client (shell). Resolves the client — reusing {@link ClientService}'s single
     * 404 contract so an unknown/malformed id yields the shared 404 envelope — derives income and essentials
     * from the client's trailing transaction window, reads the current stress score to project the impact,
     * and hands off to the pure {@link #compute}.
     *
     * @param clientId canonical client UUID (as a string)
     * @param request  the loan inputs ({@code principal}, {@code rate}, {@code term})
     * @return the enveloped-ready {@link LoanSimulateResponse}
     */
    public LoanSimulateResponse simulate(String clientId, LoanSimulateRequest request) {
        Client client = clientService.requireClient(clientId);
        int currentScore = stressScoreService.latestFor(clientId).score();

        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        List<Transaction> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(client.getId(), from, to);

        Telemetry telemetry = deriveTelemetry(window);
        return compute(request.principal(), request.rate(), request.term(),
                telemetry.income(), telemetry.essentials(), currentScore);
    }

    /**
     * Pure DESIGN §5.3 loan math over explicit inputs — no clock, no database. Reproduces the prototype:
     * amortised instalment (with the {@code r == 0} interest-free edge case), affordability verdict against
     * the client's surplus, DTI after the loan, and the projected stress-score impact.
     *
     * @param principal    loan amount P (SAR, positive)
     * @param rate         nominal annual interest rate as a percentage (non-negative; {@code 0} → {@code P/n})
     * @param term         repayment term n in months (positive)
     * @param income       the client's monthly income derived from telemetry (SAR)
     * @param essentials   the client's monthly recurring essentials derived from telemetry (SAR)
     * @param currentScore the client's current stress score, before the loan
     * @return the loan quote with instalment/total (whole SAR), DTI, verdict, band colours and projected score
     */
    LoanSimulateResponse compute(BigDecimal principal, BigDecimal rate, int term,
            BigDecimal income, BigDecimal essentials, int currentScore) {
        // r = rate/100/12; installment = P*r / (1 - (1+r)^-n), with r == 0 → P/n (no divide-by-zero).
        double r = rate.doubleValue() / 100.0 / 12.0;
        BigDecimal installment = (r == 0.0)
                ? principal.divide(BigDecimal.valueOf(term), 10, RoundingMode.HALF_UP)
                : principal.multiply(BigDecimal.valueOf(r / (1.0 - Math.pow(1.0 + r, -term))));

        BigDecimal surplus = income.subtract(essentials);

        // Verdict: installment vs fractions of surplus. When surplus <= 0 every comparison fails → red.
        String verdict;
        String verdictColor;
        if (installment.compareTo(surplus.multiply(COMFORTABLE_FRACTION)) <= 0) {
            verdict = VERDICT_COMFORTABLE;
            verdictColor = GREEN;
        } else if (installment.compareTo(surplus.multiply(STRAINS_FRACTION)) <= 0) {
            verdict = VERDICT_STRAINS;
            verdictColor = ORANGE;
        } else {
            verdict = VERDICT_NOT_AFFORDABLE;
            verdictColor = RED;
        }

        // DTI = (essentials + installment) / income (ratio). Guard a degenerate zero income defensively.
        BigDecimal dti = income.signum() > 0
                ? essentials.add(installment).divide(income, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        String dtiColor = dtiColorFor(dti);

        // Score impact: strain = installment/surplus; proj = score - max(0, strain - 0.35)*90, clamp [9,84].
        double strain = surplus.signum() > 0
                ? installment.doubleValue() / surplus.doubleValue()
                : Double.MAX_VALUE;
        long rawProjected = Math.round(currentScore
                - Math.max(0.0, strain - STRAIN_FREE_THRESHOLD) * STRAIN_PENALTY_SLOPE);
        int projectedScore = (int) Math.max(PROJECTED_SCORE_MIN, Math.min(PROJECTED_SCORE_MAX, rawProjected));

        // Round money to whole SAR for display; total = precise installment × term, then rounded.
        BigDecimal installmentSar = installment.setScale(0, RoundingMode.HALF_UP);
        BigDecimal totalSar = installment.multiply(BigDecimal.valueOf(term)).setScale(0, RoundingMode.HALF_UP);

        return new LoanSimulateResponse(installmentSar, totalSar, dti, dtiColor,
                verdict, verdictColor, projectedScore);
    }

    /** Map a DTI ratio to its DESIGN §8 band colour: {@code <70% green, <90% orange, else red} (prototype). */
    private static String dtiColorFor(BigDecimal dti) {
        long dtiPct = Math.round(dti.doubleValue() * 100.0);
        if (dtiPct < 70) {
            return GREEN;
        }
        return dtiPct < 90 ? ORANGE : RED;
    }

    /**
     * Derive the client's monthly {@code income} and recurring {@code essentials} from their transaction
     * telemetry — <em>the</em> place the prototype's hardcoded demo constants are replaced by real,
     * per-client figures (Global Rule; DESIGN §5.3).
     *
     * <p>Recurring detection mirrors {@link com.baseerah.forecast.HeuristicForecast}'s §5.2 cadence concept
     * (Step 3.1): group by {@code direction + category + cleansed description}, and treat a group as a
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
     * @param window the client's trailing {@link #WINDOW_DAYS}-day transactions (may be empty)
     * @return the derived monthly income and essentials
     */
    Telemetry deriveTelemetry(List<Transaction> window) {
        Map<String, RecurringFlow> groups = new LinkedHashMap<>();
        TreeSet<YearMonth> months = new TreeSet<>();
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (Transaction tx : window) {
            if (tx.getBookingDate() == null) {
                continue;
            }
            LocalDate date = tx.getBookingDate().atZone(UTC).toLocalDate();
            months.add(YearMonth.from(date));
            BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount().abs();
            if (tx.getDirection() == Direction.CREDIT) {
                totalCredit = totalCredit.add(amount);
            }
            String key = tx.getDirection() + "|" + tx.resolveCategory().name() + "|"
                    + (tx.getDescriptionCleansed() == null ? ""
                            : tx.getDescriptionCleansed().strip().toLowerCase(Locale.ROOT));
            groups.computeIfAbsent(key, k -> new RecurringFlow(tx.getDirection())).add(date, amount);
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
        return new Telemetry(income, essentials);
    }

    /** Monthly income and recurring essentials derived from a client's telemetry (both in SAR). */
    record Telemetry(BigDecimal income, BigDecimal essentials) {
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
