package com.baseerah.application.cashflow;

import com.baseerah.application.client.ClientService;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionRepository;
import com.baseerah.domain.kernel.Direction;
import com.baseerah.domain.kernel.LedgerEntry;
import com.baseerah.domain.stress.StressScoreCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service backing {@code GET /api/v1/clients/{id}/cashflow-summary}: the average monthly income
 * and spending shown on the Home screen. It mirrors {@code StressScoreSnapshotWriter}'s data-loading — the
 * same trailing {@link StressScoreCalculator#DEFAULT_WINDOW_DAYS}-day window mapped to domain
 * {@link LedgerEntry} records — then averages credits and debits over the distinct months that saw
 * activity. Computed live (not persisted): it is a cheap aggregate over one already-indexed window query,
 * so it needs no snapshot table of its own.
 *
 * <p>The clock is injectable so tests can pin "today" to the frozen mock-data window; production uses
 * {@link Clock#systemUTC()}.
 */
@Service
public class CashflowSummaryService {

    private final ClientService clientService;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    /**
     * @param clock the analytics clock — the day the frozen telemetry is scored against, not the wall clock
     *              (see {@code AnalyticsProperties}). Qualified by name, not by {@code ClockConfig}'s
     *              constant: the application layer must not import the composition root.
     */
    @Autowired
    public CashflowSummaryService(ClientService clientService,
            TransactionRepository transactionRepository, @Qualifier("analyticsClock") Clock clock) {
        this.clientService = clientService;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /**
     * Average monthly income and spending for a client over the trailing window. Resolves the client (→ 404
     * via {@link ClientService#requireClient} for an unknown/malformed id), loads the window, and averages
     * credits/debits over the distinct months present (so a partial month never inflates the mean).
     *
     * @param clientId canonical client UUID (as a string)
     */
    @Transactional(readOnly = true)
    public CashflowSummary summarize(String clientId) {
        ClientJpaEntity client = clientService.requireClient(clientId);
        LocalDate today = LocalDate.now(clock);

        Instant from = today.minusDays(StressScoreCalculator.DEFAULT_WINDOW_DAYS)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TransactionJpaEntity> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(client.getId(), from, to);
        List<LedgerEntry> entries = TransactionPersistenceMapper.toLedgerEntries(window);

        BigDecimal creditSum = BigDecimal.ZERO;
        BigDecimal debitSum = BigDecimal.ZERO;
        Set<YearMonth> months = new TreeSet<>();
        for (LedgerEntry e : entries) {
            months.add(YearMonth.from(e.bookingDate().atZone(ZoneOffset.UTC).toLocalDate()));
            BigDecimal amount = e.amount() == null ? BigDecimal.ZERO : e.amount().abs();
            if (e.direction() == Direction.CREDIT) {
                creditSum = creditSum.add(amount);
            } else {
                debitSum = debitSum.add(amount);
            }
        }

        BigDecimal monthCount = BigDecimal.valueOf(Math.max(1, months.size()));
        return new CashflowSummary(
                creditSum.divide(monthCount, 2, RoundingMode.HALF_UP),
                debitSum.divide(monthCount, 2, RoundingMode.HALF_UP));
    }
}
