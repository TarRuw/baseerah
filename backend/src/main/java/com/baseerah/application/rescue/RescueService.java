package com.baseerah.application.rescue;

import com.baseerah.application.infrastructure.gateway.forecast.ForecastEngine;
import com.baseerah.application.infrastructure.persistence.rescue.RescueEventPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.rescue.RescueEventRepository;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.client.ClientService;
import com.baseerah.domain.forecast.ForecastPoint;
import com.baseerah.domain.forecast.ForecastResult;
import com.baseerah.domain.rescue.RescueAssessment;
import com.baseerah.domain.rescue.RescueOption;
import com.baseerah.domain.rescue.RescueOptionType;
import com.baseerah.domain.rescue.RescueOutcome;
import com.baseerah.domain.rescue.RescueRecovery;
import com.baseerah.domain.stress.StressScoreCalculator;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application shell for Smart Rescue Mode (FR-06/07, DESIGN.md §5.4) — the imperative half whose pure
 * recovery core is {@link RescueRecovery}. Reuses the Phase-3 {@link ForecastEngine} gateway to detect an
 * upcoming cash-flow deficit, decides whether it is inside the 15-day alert lead window, prepares two
 * Sharia-aware bridge options sized from the client's telemetry, and — on confirm — computes a recovered
 * stress score and logs an auditable {@code rescue_events} row.
 *
 * <p>Depends on the {@link ForecastEngine} <em>gateway</em> and the pure {@link StressScoreCalculator}
 * (never {@code HeuristicForecastEngine} directly; ORCHESTRATION Global Rule: engines stay behind their
 * interfaces), so a Python sidecar can replace the projection later without touching this service. The
 * options it returns carry only typed facts (type/amount/term); the locale-resolved labels/details and the
 * confirmation message are resolved in {@code api/rescue/RescueWebMapper}, keeping the domain locale-free.
 *
 * <p><strong>Faithful engine (DESIGN §11 headline).</strong> {@code client_003_freelancer} is engineered
 * (db-seed/generate_personas.py) as the Smart-Rescue demo: irregular project income (no recurring inflow) and
 * a thin buffer that the steady daily burn crosses within the home horizon, so a faithful projection returns
 * a real near-term deficit — {@link RescueAssessment#alertRaised()} reflects whether it falls inside the
 * 15-day lead window. Nothing here is a fabricated constant (Global Rules): the shortfall, lead time and
 * options are all derived from the true projection over the seeded telemetry.
 */
@Service
public class RescueService {

    /** FR-06 trigger: raise the alert when the deficit is within this many days (DESIGN §5.4 "15 days before"). */
    public static final int ALERT_LEAD_DAYS = 15;

    /**
     * Horizon (days) {@link #assess} projects to find a deficit. The seeded savers stay net-positive and
     * never cross zero within a 10-year cap (→ {@link RescueAssessment#noDeficit()}), while the distressed
     * personas deficit within weeks; the generous cap simply guarantees no real deficit is missed.
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

    /** Bridge amounts are rounded up to a clean SAR unit so the offer reads as a real financing figure. */
    private static final BigDecimal AMOUNT_ROUNDING_UNIT = BigDecimal.valueOf(100);

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final ForecastEngine forecastEngine;
    private final StressScoreCalculator stressScoreCalculator;
    private final TransactionRepository transactionRepository;
    private final ClientService clientService;
    private final RescueEventRepository rescueEventRepository;

    public RescueService(ForecastEngine forecastEngine, StressScoreCalculator stressScoreCalculator,
            TransactionRepository transactionRepository, ClientService clientService,
            RescueEventRepository rescueEventRepository) {
        this.forecastEngine = forecastEngine;
        this.stressScoreCalculator = stressScoreCalculator;
        this.transactionRepository = transactionRepository;
        this.clientService = clientService;
        this.rescueEventRepository = rescueEventRepository;
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
        ClientJpaEntity client = clientService.requireClient(clientId.toString());
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

        List<RescueOption> options = buildOptions(shortfall);
        return new RescueAssessment(true, deficitDate, deficitInDays, alertRaised, shortfall, options);
    }

    /**
     * Confirm a chosen bridge option (FR-07): compute the current stress score, the recovered score once the
     * bridge removes the deficit, persist a {@code rescue_events} row, and return the before/after outcome.
     *
     * @param clientId the client being rescued
     * @param option   the bridge option the client chose (one of the two from {@link #assess})
     * @return the before/after stress score
     * @throws IllegalStateException if the client has no active deficit to rescue
     */
    @Transactional
    public RescueOutcome confirm(UUID clientId, RescueOption option) {
        ClientJpaEntity client = clientService.requireClient(clientId.toString());
        RescueAssessment assessment = assess(clientId);
        if (!assessment.hasDeficit()) {
            throw new IllegalStateException("No active deficit to rescue for client " + clientId);
        }

        // The projection's start day, recovered from the assessment, anchors the scoring window.
        LocalDate today = assessment.deficitDate().minusDays(assessment.deficitInDays());
        List<TransactionJpaEntity> window = trailingWindow(client.getId(), today);
        int scoreBefore = stressScoreCalculator
                .calculate(TransactionPersistenceMapper.toLedgerEntries(window)).score();
        int scoreAfter = RescueRecovery.recoveredScore(scoreBefore, option);
        RescueOutcome outcome = new RescueOutcome(scoreBefore, scoreAfter);

        rescueEventRepository.save(
                RescueEventPersistenceMapper.toJpaEntity(client, assessment, option, outcome));
        return outcome;
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
     * The single self-service bridge option (FR-07): liquidate safe fund assets to cover the shortfall — no
     * financing cost, no repayment term. The financed alternative is no longer an auto-priced Murabaha with a
     * hardcoded rate; it is now a request-for-proposal to the client's banks (the {@code financing} slice),
     * each returning its own rate. Amounts are rounded up to a clean {@value #AMOUNT_ROUNDING_UNIT} SAR unit;
     * the locale-resolved label/detail are added by the web mapper.
     */
    private List<RescueOption> buildOptions(BigDecimal shortfall) {
        BigDecimal amount = roundUpToUnit(shortfall);
        return List.of(new RescueOption(RescueOptionType.LIQUIDATE, amount, null));
    }

    // ── Telemetry helpers ──────────────────────────────────────────────────────────────────────────

    /** The client's trailing {@value #WINDOW_DAYS}-day transactions ending at {@code today}. */
    private List<TransactionJpaEntity> trailingWindow(UUID clientId, LocalDate today) {
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        return transactionRepository.findByAccount_Client_IdAndBookingDateBetween(clientId, from, to);
    }

    /** Round a SAR amount up to the next {@value #AMOUNT_ROUNDING_UNIT} unit, so the offer reads cleanly. */
    private static BigDecimal roundUpToUnit(BigDecimal value) {
        return value.divide(AMOUNT_ROUNDING_UNIT, 0, RoundingMode.CEILING).multiply(AMOUNT_ROUNDING_UNIT);
    }
}
