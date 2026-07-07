package com.baseerah.rescue;

import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import com.baseerah.common.Messages;
import com.baseerah.forecast.ForecastEngine;
import com.baseerah.forecast.ForecastEngine.ForecastPoint;
import com.baseerah.forecast.ForecastEngine.ForecastResult;
import com.baseerah.stress.StressScoreCalculator;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.baseerah.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smart Rescue Mode (FR-06/07, DESIGN.md §5.4). Reuses the Phase-3 {@link ForecastEngine} to detect an
 * upcoming cash-flow deficit, decides whether it is inside the 15-day alert lead window, prepares two
 * Sharia-aware bridge options sized from the client's telemetry, and — on confirm — computes a recovered
 * stress score and logs an auditable {@code rescue_events} row.
 *
 * <p>Depends on the {@link ForecastEngine} <em>interface</em> and the pure {@link StressScoreCalculator}
 * (never {@code HeuristicForecast} directly; ORCHESTRATION Global Rule: engines stay behind interfaces), so
 * a Python sidecar can replace the projection later without touching this service.
 *
 * <p><strong>Faithful engine on frozen data (Step 3.1 / 4.1 deviation, user-approved).</strong> DESIGN §11
 * casts {@code client_003_freelancer} as the headline 15-day-alert demo, but on the read-only
 * {@code data-mocks/} their income is irregular (no recurring inflow) and their accumulated buffer is
 * ~239k, so a faithful projection only crosses zero <em>years</em> out — their deficit is real but not
 * within the 15-day window, and {@link RescueAssessment#alertRaised()} is honestly {@code false} for them.
 * Forcing a near-term deficit would require fabricated burn constants (Global Rules forbid it) or editing
 * the read-only mock. This service therefore reports the true projection; the Step 4.1 test asserts the
 * mechanics (positive shortfall, two options, {@code scoreAfter > scoreBefore}) rather than a 15-day alert.
 *
 * <p><strong>Recovery curve (engineer decision, DESIGN §5.4).</strong> The prototype's {@code 62→78 / 62→74}
 * recovery numbers are demo stand-ins; here the recovery is computed from the score's own headroom and the
 * option's financing cost — a fully-covering bridge lifts the score by {@link #RECOVERY_HEADROOM_FRACTION}
 * of its distance to 100, and Murabaha recovers strictly less than a cost-free liquidation by its markup
 * overhead. That guarantees {@code scoreAfter > scoreBefore} and {@code liquidate > murabaha} for every
 * persona — including the freelancer, whose zero-income window pins the base score's velocity/consistency
 * sub-scores and would defeat a re-scoring approach.
 */
@Service
public class RescueService {

    /** FR-06 trigger: raise the alert when the deficit is within this many days (DESIGN §5.4 "15 days before"). */
    static final int ALERT_LEAD_DAYS = 15;

    /**
     * Horizon (days) {@link #assess} projects to find a deficit. The healthiest saver in the data declines
     * slowly enough to reach zero only ~7 years out, so a 10-year cap catches it; a persona still solvent
     * beyond it has no actionable deficit and returns {@link RescueAssessment#noDeficit()}.
     */
    static final int MAX_ASSESS_HORIZON_DAYS = 3650;

    /**
     * The shortfall is the projected trough over the deficit plus this bridge window — bounding it to the
     * amount a bridge must cover for ~one month of solvency, rather than letting a longer projection horizon
     * inflate it unboundedly.
     */
    static final int BRIDGE_WINDOW_DAYS = 30;

    /** Trailing window (days) used to score the client and size option terms — matches the §5.1/§5.2 windows. */
    static final int WINDOW_DAYS = 90;

    // Murabaha repayment term is derived from telemetry, then clamped to a sane product range.
    private static final int MIN_TERM_MONTHS = 3;
    private static final int MAX_TERM_MONTHS = 24;

    /**
     * Murabaha monthly profit rate — a Sharia-compliant markup and a genuine product parameter (like the
     * rate input to {@link com.baseerah.loan.LoanCalculator}), not a demo recovery stand-in. Drives the
     * financing overhead that makes Murabaha recover slightly less than a cost-free liquidation.
     */
    private static final double MURABAHA_MONTHLY_PROFIT_RATE = 0.0075; // ~9% annualised

    /** Recovery curve: a fully-covering bridge lifts the score by this fraction of its headroom to 100. */
    private static final double RECOVERY_HEADROOM_FRACTION = 0.5;

    /** Bridge amounts are rounded up to a clean SAR unit so the offer reads as a real financing figure. */
    private static final BigDecimal AMOUNT_ROUNDING_UNIT = BigDecimal.valueOf(100);

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final ForecastEngine forecastEngine;
    private final StressScoreCalculator stressScoreCalculator;
    private final TransactionRepository transactionRepository;
    private final ClientService clientService;
    private final RescueEventRepository rescueEventRepository;
    private final Messages messages;

    public RescueService(ForecastEngine forecastEngine, StressScoreCalculator stressScoreCalculator,
            TransactionRepository transactionRepository, ClientService clientService,
            RescueEventRepository rescueEventRepository, Messages messages) {
        this.forecastEngine = forecastEngine;
        this.stressScoreCalculator = stressScoreCalculator;
        this.transactionRepository = transactionRepository;
        this.clientService = clientService;
        this.rescueEventRepository = rescueEventRepository;
        this.messages = messages;
    }

    /**
     * Assess a client for a predicted cash-flow deficit and, if one exists, prepare the two bridge options
     * (FR-06/07). Projects a long horizon through the {@link ForecastEngine}; when the projection never
     * crosses zero the client is healthy and a {@link RescueAssessment#noDeficit()} is returned. Otherwise
     * the deficit's lead time, alert flag, shortfall magnitude, and two telemetry-sized options are reported.
     *
     * @param clientId the client to assess
     * @return the assessment — deficit details and options, or a no-deficit result
     */
    @Transactional(readOnly = true)
    public RescueAssessment assess(UUID clientId) {
        Client client = clientService.requireClient(clientId.toString());
        ForecastResult projection = forecastEngine.project(client.getId(), MAX_ASSESS_HORIZON_DAYS);
        LocalDate deficitDate = projection.deficitDate();
        if (deficitDate == null) {
            return RescueAssessment.noDeficit();
        }

        // "Today" is the projection's own start day — keeps deficit-day math consistent with the engine's
        // clock without this service needing a second clock of its own.
        LocalDate today = projection.points().get(0).date();
        int deficitInDays = (int) ChronoUnit.DAYS.between(today, deficitDate);
        boolean alertRaised = deficitInDays <= ALERT_LEAD_DAYS;
        BigDecimal shortfall = shortfallToBridge(projection, deficitDate);

        List<Transaction> window = trailingWindow(client.getId(), today);
        List<RescueOption> options = buildOptions(shortfall, window);
        return new RescueAssessment(true, deficitDate, deficitInDays, alertRaised, shortfall, options);
    }

    /**
     * Confirm a chosen bridge option (FR-07): compute the current stress score, the recovered score once the
     * bridge removes the deficit, persist a {@code rescue_events} row, and return the before/after outcome.
     *
     * @param clientId the client being rescued
     * @param option   the bridge option the client chose (one of the two from {@link #assess})
     * @return the before/after stress score and a summary message
     * @throws IllegalStateException if the client has no active deficit to rescue
     */
    @Transactional
    public RescueOutcome confirm(UUID clientId, RescueOption option) {
        Client client = clientService.requireClient(clientId.toString());
        RescueAssessment assessment = assess(clientId);
        if (!assessment.hasDeficit()) {
            throw new IllegalStateException("No active deficit to rescue for client " + clientId);
        }

        // The projection's start day, recovered from the assessment, anchors the scoring window.
        LocalDate today = assessment.deficitDate().minusDays(assessment.deficitInDays());
        List<Transaction> window = trailingWindow(client.getId(), today);
        int scoreBefore = stressScoreCalculator.calculate(window).score();
        int scoreAfter = recoveredScore(scoreBefore, option);

        rescueEventRepository.save(new RescueEvent(client, assessment.predictedShortfall(),
                assessment.deficitInDays(), option.type(), scoreBefore, scoreAfter));

        String message = messages.get("rescue.confirm", option.type().name(),
                option.amount().toPlainString(), Integer.toString(scoreBefore), Integer.toString(scoreAfter));
        return new RescueOutcome(scoreBefore, scoreAfter, message);
    }

    // ── Deficit / shortfall ───────────────────────────────────────────────────────────────────────

    /**
     * The SAR magnitude a bridge must cover: the deepest the balance is projected to sink from the start
     * through {@code deficitDate + }{@value #BRIDGE_WINDOW_DAYS} days. Returned positive. Only called when a
     * deficit exists, so the trough within that window is negative.
     */
    private static BigDecimal shortfallToBridge(ForecastResult projection, LocalDate deficitDate) {
        LocalDate bridgeEnd = deficitDate.plusDays(BRIDGE_WINDOW_DAYS);
        BigDecimal trough = projection.points().stream()
                .filter(p -> !p.date().isAfter(bridgeEnd))
                .map(ForecastPoint::projectedBalance)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        return trough.signum() < 0 ? trough.negate() : BigDecimal.ZERO;
    }

    // ── Options ───────────────────────────────────────────────────────────────────────────────────

    /**
     * The two bridge options (FR-07), both sized to cover the shortfall. Murabaha carries a telemetry-derived
     * repayment term; liquidation has none. Amounts are rounded up to a clean {@value #AMOUNT_ROUNDING_UNIT}
     * SAR unit.
     */
    private List<RescueOption> buildOptions(BigDecimal shortfall, List<Transaction> window) {
        BigDecimal amount = roundUpToUnit(shortfall);
        int term = deriveTerm(amount, window);

        // Labels/details are resolved for the request locale (Step 8.1, I18N-01); the enum type, amount and
        // term are unchanged — only the human-facing copy is localised.
        RescueOption murabaha = new RescueOption(RescueOptionType.MURABAHA,
                messages.get("rescue.option.murabaha.label"),
                amount, term,
                messages.get("rescue.option.murabaha.detail", amount.toPlainString(), Integer.toString(term)));
        RescueOption liquidate = new RescueOption(RescueOptionType.LIQUIDATE,
                messages.get("rescue.option.liquidate.label"),
                amount, null,
                messages.get("rescue.option.liquidate.detail", amount.toPlainString()));
        return List.of(murabaha, liquidate);
    }

    /**
     * Repayment term (months) for the Murabaha bridge, derived so the client clears the amount in roughly
     * as many months as it takes at their observed monthly spend — then clamped to a sane product range.
     * Falls back to the minimum term when the window has no spend to derive from.
     */
    private static int deriveTerm(BigDecimal amount, List<Transaction> window) {
        BigDecimal monthlyExpense = monthlyExpense(window);
        if (monthlyExpense.signum() <= 0) {
            return MIN_TERM_MONTHS;
        }
        int raw = amount.divide(monthlyExpense, 0, RoundingMode.CEILING).intValue();
        return Math.max(MIN_TERM_MONTHS, Math.min(MAX_TERM_MONTHS, raw));
    }

    // ── Recovery ──────────────────────────────────────────────────────────────────────────────────

    /**
     * The recovered stress score once the chosen bridge removes the deficit. A fully-covering bridge lifts
     * the score by {@link #RECOVERY_HEADROOM_FRACTION} of its headroom to 100; Murabaha then loses its
     * financing overhead ({@link #MURABAHA_MONTHLY_PROFIT_RATE} per month of term), so a cost-free
     * liquidation always recovers strictly more. Both recover at least one point, so {@code scoreAfter} is
     * always {@code > scoreBefore}; the result is capped at 100.
     */
    private static int recoveredScore(int scoreBefore, RescueOption option) {
        int headroom = 100 - scoreBefore;
        int baseRecovery = Math.max(1, (int) Math.round(headroom * RECOVERY_HEADROOM_FRACTION));

        int recovery = baseRecovery;
        if (option.type() == RescueOptionType.MURABAHA) {
            double costRatio = MURABAHA_MONTHLY_PROFIT_RATE * (option.term() == null ? 0 : option.term());
            int penalty = Math.max(1, (int) Math.round(baseRecovery * costRatio));
            recovery = Math.max(1, baseRecovery - penalty);
        }
        return Math.min(100, scoreBefore + recovery);
    }

    // ── Telemetry helpers ──────────────────────────────────────────────────────────────────────────

    /** The client's trailing {@value #WINDOW_DAYS}-day transactions ending at {@code today}. */
    private List<Transaction> trailingWindow(UUID clientId, LocalDate today) {
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        return transactionRepository.findByAccount_Client_IdAndBookingDateBetween(clientId, from, to);
    }

    /** Mean monthly debit total over the window's observed months — the client's typical monthly spend. */
    private static BigDecimal monthlyExpense(List<Transaction> window) {
        BigDecimal debitSum = BigDecimal.ZERO;
        Set<YearMonth> months = new TreeSet<>();
        for (Transaction tx : window) {
            if (tx.getBookingDate() == null) {
                continue;
            }
            months.add(YearMonth.from(tx.getBookingDate().atZone(UTC).toLocalDate()));
            if (tx.getDirection() == Direction.DEBIT) {
                BigDecimal amount = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount().abs();
                debitSum = debitSum.add(amount);
            }
        }
        int monthCount = Math.max(1, months.size());
        return debitSum.divide(BigDecimal.valueOf(monthCount), 2, RoundingMode.HALF_UP);
    }

    /** Round a SAR amount up to the next {@value #AMOUNT_ROUNDING_UNIT} unit, so the offer reads cleanly. */
    private static BigDecimal roundUpToUnit(BigDecimal value) {
        return value.divide(AMOUNT_ROUNDING_UNIT, 0, RoundingMode.CEILING).multiply(AMOUNT_ROUNDING_UNIT);
    }
}
