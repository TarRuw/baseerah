package com.baseerah.application.infrastructure.gateway.forecast;

import com.baseerah.application.infrastructure.persistence.expense.DeclaredExpensePersistenceMapper;
import com.baseerah.application.infrastructure.persistence.expense.DeclaredExpenseRepository;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionPersistenceMapper;
import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.domain.forecast.BalanceProjection;
import com.baseerah.domain.forecast.ForecastResult;
import com.baseerah.domain.kernel.LedgerEntry;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Default {@link ForecastEngine}: the imperative shell around the pure {@link BalanceProjection} core
 * (DESIGN.md §5.2). This shell reads the clock and the database to assemble the trailing window, maps each
 * row to a {@link LedgerEntry} (the domain view), then delegates the projection math to
 * {@link BalanceProjection#project} — the same functional-core / imperative-shell split as the stress
 * calculator, so the projection is unit-testable with no Spring or Postgres.
 *
 * <p>The trailing window is {@value #WINDOW_DAYS} days (DESIGN §5.2); everything downstream — recurring
 * inflow/outflow detection and the day-by-day walk — lives in the framework-free core. The
 * {@code TransactionJpaEntity → LedgerEntry} mapping reuses the static {@link TransactionPersistenceMapper}, the single
 * place that reproduces the entity's category resolution for the algorithms.
 */
@Service
public class HeuristicForecastEngine implements ForecastEngine {

    /** Trailing window (days) the shell loads from the database and feeds to the core — DESIGN §5.2. */
    public static final int WINDOW_DAYS = 90;

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final TransactionRepository transactionRepository;
    private final DeclaredExpenseRepository declaredExpenseRepository;
    private final Clock clock;

    /**
     * @param clock the analytics clock — the day the frozen telemetry is scored against, not the wall clock
     *              (see {@code AnalyticsProperties}). Qualified by name rather than by the constant on
     *              {@code ClockConfig}: the application layer must not import the composition root, and
     *              {@code LayeringTest} enforces that.
     */
    @Autowired
    public HeuristicForecastEngine(TransactionRepository transactionRepository,
            DeclaredExpenseRepository declaredExpenseRepository, @Qualifier("analyticsClock") Clock clock) {
        this.transactionRepository = transactionRepository;
        this.declaredExpenseRepository = declaredExpenseRepository;
        this.clock = clock;
    }

    @Override
    public ForecastResult project(UUID clientId, int horizonDays) {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        List<TransactionJpaEntity> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(clientId, from, to);
        List<LedgerEntry> ledger = TransactionPersistenceMapper.toLedgerEntries(window);
        // Declared periodic expenses (Step 11.3) widen the projection with recurring outflows the SAMA feed
        // cannot see; only active ones are loaded, so a deactivated expense drops out of the forecast.
        List<DeclaredExpense> declared = declaredExpenseRepository.findByClient_IdAndActiveTrue(clientId).stream()
                .map(DeclaredExpensePersistenceMapper::toDomain)
                .toList();
        return BalanceProjection.project(ledger, declared, today, horizonDays);
    }
}
