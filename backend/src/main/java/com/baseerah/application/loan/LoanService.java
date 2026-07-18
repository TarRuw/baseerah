package com.baseerah.application.loan;

import com.baseerah.application.infrastructure.persistence.transaction.TransactionPersistenceMapper;
import com.baseerah.application.stress.StressScoreService;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.client.ClientService;
import com.baseerah.domain.kernel.LedgerEntry;
import com.baseerah.domain.loan.LoanCalculator;
import com.baseerah.domain.loan.LoanInputs;
import com.baseerah.domain.loan.LoanQuote;
import com.baseerah.domain.loan.LoanTelemetry;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Application shell for the loan-affordability simulator (FR-05, DESIGN.md §5.3) — the imperative half of the
 * functional-core/imperative-shell split whose pure core is {@link LoanCalculator}. Resolves the client
 * (reusing {@link ClientService}'s single 404 contract), loads the trailing transaction window and the
 * current stress score, maps the window to the domain {@link LedgerEntry} view (via the shared
 * {@link TransactionPersistenceMapper} — the same static mapper the stress and forecast slices use, so the domain
 * never sees a JPA entity), derives per-client income/essentials, and hands an explicit {@link LoanInputs} to
 * the pure calculator. Returns the domain {@link LoanQuote}; the web layer resolves colours and localizes.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}: loan simulation persists nothing, and
 * {@link StressScoreService#latestFor} manages (and may write within) its own transaction — wrapping it in an
 * outer read-only transaction here would change that behaviour. This matches the pre-refactor shell exactly.
 */
@Service
public class LoanService {

    /** Trailing window (days) loaded to derive income/essentials — matches the §5.1/§5.2 windows. */
    public static final int WINDOW_DAYS = 90;

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private final ClientService clientService;
    private final TransactionRepository transactionRepository;
    private final StressScoreService stressScoreService;
    private final LoanCalculator loanCalculator;
    private final Clock clock;

    /**
     * @param clock the analytics clock — the day the frozen telemetry is scored against, not the wall clock
     *              (see {@code AnalyticsProperties}). Qualified by name, not by {@code ClockConfig}'s
     *              constant: the application layer must not import the composition root.
     */
    @Autowired
    public LoanService(ClientService clientService, TransactionRepository transactionRepository,
            StressScoreService stressScoreService, LoanCalculator loanCalculator,
            @Qualifier("analyticsClock") Clock clock) {
        this.clientService = clientService;
        this.transactionRepository = transactionRepository;
        this.stressScoreService = stressScoreService;
        this.loanCalculator = loanCalculator;
        this.clock = clock;
    }

    /**
     * Simulate a loan for a client. Resolves the client — reusing {@link ClientService}'s single 404 contract
     * so an unknown/malformed id yields the shared 404 envelope — derives income and essentials from the
     * client's trailing {@link #WINDOW_DAYS}-day ledger window, reads the current stress score to project the
     * impact, and hands off to the pure {@link LoanCalculator#compute}.
     *
     * @param clientId  canonical client UUID (as a string)
     * @param principal loan amount P (SAR)
     * @param rate      nominal annual interest rate as a percentage
     * @param term      repayment term n in months
     * @return the domain {@link LoanQuote}
     */
    public LoanQuote simulate(String clientId, BigDecimal principal, BigDecimal rate, int term) {
        ClientJpaEntity client = clientService.requireClient(clientId);
        int currentScore = stressScoreService.latestFor(clientId).score();

        LocalDate today = LocalDate.now(clock);
        Instant from = today.minusDays(WINDOW_DAYS).atStartOfDay(UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(UTC).toInstant();
        List<TransactionJpaEntity> window = transactionRepository
                .findByAccount_Client_IdAndBookingDateBetween(client.getId(), from, to);
        List<LedgerEntry> entries = TransactionPersistenceMapper.toLedgerEntries(window);

        LoanTelemetry telemetry = loanCalculator.deriveTelemetry(entries);
        return loanCalculator.compute(new LoanInputs(
                principal, rate, term, telemetry.income(), telemetry.essentials(), currentScore));
    }
}
