package com.baseerah.stress;

import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import com.baseerah.transaction.Transaction;
import com.baseerah.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The imperative shell around {@link StressScoreCalculator}: it reads the clock and the database so the
 * calculator can stay pure. For a client it loads the trailing {@link StressScoreCalculator#DEFAULT_WINDOW_DAYS}-day
 * transaction window, computes the score, and upserts exactly one {@code stress_scores} row for today —
 * keyed by the {@code (client_id, as_of_date)} unique constraint, so a second run on the same day updates
 * rather than duplicates (DESIGN.md §5.1).
 *
 * <p>The clock is injectable so tests can pin "today"; production uses {@link Clock#systemUTC()}.
 */
@Service
public class StressScoreSnapshotWriter {

    private final StressScoreCalculator calculator;
    private final StressScoreRepository stressScoreRepository;
    private final TransactionRepository transactionRepository;
    private final ClientService clientService;
    private final Clock clock;

    @Autowired
    public StressScoreSnapshotWriter(StressScoreCalculator calculator,
            StressScoreRepository stressScoreRepository, TransactionRepository transactionRepository,
            ClientService clientService) {
        this(calculator, stressScoreRepository, transactionRepository, clientService, Clock.systemUTC());
    }

    // Public so integration tests in other packages can pin "today" to the frozen mock-data window and get
    // deterministic, machine-date-independent zones (the same fixed-clock technique the gamification tests use).
    public StressScoreSnapshotWriter(StressScoreCalculator calculator,
            StressScoreRepository stressScoreRepository, TransactionRepository transactionRepository,
            ClientService clientService, Clock clock) {
        this.calculator = calculator;
        this.stressScoreRepository = stressScoreRepository;
        this.transactionRepository = transactionRepository;
        this.clientService = clientService;
        this.clock = clock;
    }

    /**
     * Compute and persist today's snapshot for a client. Resolves the client (404 contract reused from
     * {@link ClientService#requireClient}), scores the trailing window, and upserts the day's row.
     *
     * @param clientId canonical client UUID (as a string)
     * @return the persisted snapshot for {@code as_of_date = today}
     */
    @Transactional
    public StressScore writeSnapshot(String clientId) {
        Client client = clientService.requireClient(clientId);
        LocalDate today = LocalDate.now(clock);

        Instant from = today.minusDays(StressScoreCalculator.DEFAULT_WINDOW_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Transaction> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(client.getId(), from, to);

        StressScoreResult result = calculator.calculate(window);
        BigDecimal velocity = toPercent(result.velocitySubScore());
        BigDecimal consistency = toPercent(result.consistencySubScore());
        BigDecimal liability = toPercent(result.liabilitySubScore());

        StressScore snapshot = stressScoreRepository
                .findByClientIdAndAsOfDate(client.getId(), today)
                .map(existing -> {
                    existing.update(result.score(), result.zone(), velocity, consistency, liability);
                    return existing;
                })
                .orElseGet(() -> new StressScore(client, today, result.score(), result.zone(),
                        velocity, consistency, liability));

        return stressScoreRepository.save(snapshot);
    }

    /** Scale a {@code [0,1]} sub-score to the stored 0–100 scale, rounded to the column's 2 decimals. */
    private static BigDecimal toPercent(double subScore) {
        return BigDecimal.valueOf(subScore * 100.0).setScale(2, RoundingMode.HALF_UP);
    }
}
