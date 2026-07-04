package com.baseerah.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseerah.account.Account;
import com.baseerah.client.Client;
import com.baseerah.forecast.ForecastEngine;
import com.baseerah.forecast.ForecastEngine.ForecastPoint;
import com.baseerah.forecast.ForecastEngine.ForecastResult;
import com.baseerah.stress.StressScoreCalculator;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.baseerah.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnderwritingService} — no Spring, no database. The pure {@link
 * UnderwritingService#assemble} core is driven directly with a hand-crafted telemetry window and forecast
 * projection to pin the three §5.5 verdict bands (OK / WARN / BAD), and a mock-backed {@link
 * UnderwritingService#generateReport} test verifies the report is computed <em>through</em> the injected
 * {@link ForecastEngine} (forecast reuse, Global Rule) and persisted onto the application row.
 *
 * <p>Stamina is a pure function of the projection, so the bands are reached by crafting {@link ForecastResult}s
 * (no-deficit high-retention → high stamina; no-deficit zero-retention → mid stamina; early deep deficit →
 * low stamina) and by sizing the requested amount (an oversized ask drives DTI past the 71% floor). A real
 * {@link StressScoreCalculator} supplies income stability from the same window.
 */
class UnderwritingServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final ZoneOffset UTC = ZoneOffset.UTC;

    /** Service whose forecast engine and repositories are unused by the pure {@code assemble} path. */
    private UnderwritingService pureService() {
        return new UnderwritingService(mock(ForecastEngine.class), new StressScoreCalculator(),
                mock(TransactionRepository.class), mock(LoanApplicationRepository.class));
    }

    // ── Verdict bands via the pure core ────────────────────────────────────────────────────────────

    @Test
    void okBand_highStaminaAndLowDti() {
        UnderwritingService service = pureService();
        LoanApplication app = application(new BigDecimal("50000.00"));
        // No deficit, buffer 100k → trough 90k (retention 0.9) → stamina 96; small ask → DTI ~25%.
        ForecastResult projection = projection(null, 100_000, 90_000);

        UnderwritingReport report = service.assemble(app, healthyWindow(), projection);

        assertThat(report.staminaScore()).as("strong endurance").isGreaterThanOrEqualTo(70);
        assertThat(report.forecastDti()).as("comfortable DTI").isLessThanOrEqualTo(new BigDecimal("34"));
        assertThat(report.verdict()).isEqualTo(Verdict.OK);
        assertThat(report.riskTier()).isEqualTo("A");
        assertThat(report.incomeStability()).as("steady salary → high stability")
                .isGreaterThanOrEqualTo(new BigDecimal("90"));
        assertThat(report.defaultProb12mo()).as("PD is a percentage in range")
                .isBetween(BigDecimal.ZERO, new BigDecimal("60"));
    }

    @Test
    void warnBand_midStaminaAndLowDti() {
        UnderwritingService service = pureService();
        LoanApplication app = application(new BigDecimal("50000.00"));
        // No deficit (survival 1) but buffer fully eroded to 0 at the trough (retention 0) → stamina 60.
        ForecastResult projection = projection(null, 100_000, 0);

        UnderwritingReport report = service.assemble(app, healthyWindow(), projection);

        assertThat(report.staminaScore()).as("mixed endurance").isBetween(49, 69);
        assertThat(report.forecastDti()).isLessThan(new BigDecimal("71"));
        assertThat(report.verdict()).isEqualTo(Verdict.WARN);
        assertThat(report.riskTier()).isEqualTo("B");
    }

    @Test
    void badBand_lowStamina() {
        UnderwritingService service = pureService();
        LoanApplication app = application(new BigDecimal("50000.00"));
        // Early, deep deficit (day 20 of 365, trough -5k on a 10k buffer) → stamina collapses ≤ 48.
        ForecastResult projection = projection(START.plusDays(20), 10_000, -5_000);

        UnderwritingReport report = service.assemble(app, healthyWindow(), projection);

        assertThat(report.staminaScore()).as("fragile endurance").isLessThanOrEqualTo(48);
        assertThat(report.verdict()).isEqualTo(Verdict.BAD);
        assertThat(report.riskTier()).isEqualTo("C");
    }

    @Test
    void badBand_dtiAloneTriggersRegardlessOfStamina() {
        UnderwritingService service = pureService();
        // Oversized ask: servicing dwarfs income → DTI ≥ 71% → BAD even with a strong (96) stamina.
        LoanApplication app = application(new BigDecimal("5000000.00"));
        ForecastResult projection = projection(null, 100_000, 90_000);

        UnderwritingReport report = service.assemble(app, healthyWindow(), projection);

        assertThat(report.staminaScore()).as("stamina is still strong").isGreaterThanOrEqualTo(70);
        assertThat(report.forecastDti()).as("DTI blows past the 71% floor")
                .isGreaterThanOrEqualTo(new BigDecimal("71"));
        assertThat(report.verdict()).isEqualTo(Verdict.BAD);
    }

    /** The §5.5 thresholds pinned directly, independent of the metric derivations. */
    @Test
    void verdictThresholdsMatchDesign() {
        // OK: stamina ≥ 70 AND DTI ≤ 34.
        assertThat(UnderwritingService.verdictFor(70, new BigDecimal("34"))).isEqualTo(Verdict.OK);
        // WARN: strong stamina but DTI just over the OK ceiling; not yet the BAD floor.
        assertThat(UnderwritingService.verdictFor(85, new BigDecimal("35"))).isEqualTo(Verdict.WARN);
        assertThat(UnderwritingService.verdictFor(60, new BigDecimal("20"))).isEqualTo(Verdict.WARN);
        // BAD: either clause alone suffices.
        assertThat(UnderwritingService.verdictFor(48, new BigDecimal("10"))).isEqualTo(Verdict.BAD);
        assertThat(UnderwritingService.verdictFor(90, new BigDecimal("71"))).isEqualTo(Verdict.BAD);
    }

    // ── Shell: forecast reuse + persistence ────────────────────────────────────────────────────────

    @Test
    void generateReportUsesForecastEngineAndPersistsReport() {
        ForecastEngine forecastEngine = mock(ForecastEngine.class);
        TransactionRepository txRepo = mock(TransactionRepository.class);
        LoanApplicationRepository appRepo = mock(LoanApplicationRepository.class);
        UnderwritingService service =
                new UnderwritingService(forecastEngine, new StressScoreCalculator(), txRepo, appRepo);

        UUID applicationId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        LoanApplication app = new LoanApplication("APP-TEST", "Test Applicant", "TA",
                "Working capital", new BigDecimal("50000.00"), clientId);

        when(appRepo.findById(applicationId)).thenReturn(Optional.of(app));
        when(forecastEngine.project(eq(clientId), anyInt())).thenReturn(projection(null, 100_000, 90_000));
        when(txRepo.findByAccount_Client_IdAndBookingDateBetween(eq(clientId), any(), any()))
                .thenReturn(healthyWindow());
        when(appRepo.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        UnderwritingReport report = service.generateReport(applicationId);

        // Forecast reuse: the 12-month projection came through the ForecastEngine interface.
        verify(forecastEngine).project(eq(clientId), eq(UnderwritingService.STAMINA_HORIZON_DAYS));
        // A full report is produced …
        assertThat(report.staminaScore()).isBetween(0, 100);
        assertThat(report.forecastDti()).isNotNull();
        assertThat(report.incomeStability()).isNotNull();
        assertThat(report.defaultProb12mo()).isNotNull();
        assertThat(report.verdict()).isNotNull();
        assertThat(report.riskTier()).isNotNull();
        // … and stamped back onto the row.
        verify(appRepo).save(app);
        assertThat(app.getVerdict()).isEqualTo(report.verdict());
        assertThat(app.getStaminaScore()).isEqualTo(report.staminaScore());
        assertThat(app.getForecastDti()).isEqualByComparingTo(report.forecastDti());
    }

    @Test
    void generateReportRejectsUnlinkedApplicant() {
        LoanApplicationRepository appRepo = mock(LoanApplicationRepository.class);
        UnderwritingService service = new UnderwritingService(mock(ForecastEngine.class),
                new StressScoreCalculator(), mock(TransactionRepository.class), appRepo);

        UUID applicationId = UUID.randomUUID();
        LoanApplication synthetic = new LoanApplication("APP-SYN", "Synthetic", "SY",
                "Renovation", new BigDecimal("45000.00"), null);
        when(appRepo.findById(applicationId)).thenReturn(Optional.of(synthetic));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateReport(applicationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no linked client telemetry");
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

    /** A loan application for a linked client, requesting {@code amount}. */
    private static LoanApplication application(BigDecimal amount) {
        return new LoanApplication("APP-FIX", "Fixture Applicant", "FA", "Test purpose",
                amount, UUID.randomUUID());
    }

    /**
     * A healthy three-month window: steady SAR 20,000 monthly salary and recurring SAR 4,000 rent, with a
     * SAR 100,000 buffer on the latest transaction. Yields monthly income 20k, obligations 4k, and perfectly
     * steady income (stability ≈ 100%).
     */
    private static List<Transaction> healthyWindow() {
        List<Transaction> window = new ArrayList<>();
        LocalDate[] monthStarts = {START.minusMonths(2), START.minusMonths(1), START};
        for (int i = 0; i < monthStarts.length; i++) {
            LocalDate month = monthStarts[i];
            boolean latest = i == monthStarts.length - 1;
            window.add(tx(Direction.CREDIT, "20000.00", "SALARY", "monthly salary", month.withDayOfMonth(25),
                    latest ? "100000.00" : "80000.00"));
            window.add(tx(Direction.DEBIT, "4000.00", "UTILITIES", "apartment rent", month.withDayOfMonth(1),
                    "96000.00"));
        }
        return window;
    }

    private static Transaction tx(Direction direction, String amount, String category, String desc,
            LocalDate date, String closingBalance) {
        Client client = new Client("client_test", "Test", "test");
        Account account = new Account(client, "acc-test", "Test Bank", "#000000", "SAR",
                new BigDecimal("100000.00"), "TKN-TEST");
        return new Transaction(account, UUID.randomUUID().toString(), direction, new BigDecimal(amount),
                "SAR", desc, desc, category, new BigDecimal("0.95"),
                date.atStartOfDay(UTC).toInstant(), new BigDecimal(closingBalance));
    }

    /**
     * A 12-month {@link ForecastResult} reduced to what {@link UnderwritingService#staminaScore} reads: a
     * first point (the start day at {@code baseBalance}) and a last point (start + 365d) fixing the horizon,
     * the {@code deficitDate} (nullable), and the {@code troughBalance} as the minimum projected balance.
     */
    private static ForecastResult projection(LocalDate deficitDate, double baseBalance, double troughBalance) {
        List<ForecastPoint> points = List.of(
                new ForecastPoint(START, BigDecimal.valueOf(baseBalance)),
                new ForecastPoint(START.plusDays(365), BigDecimal.valueOf(troughBalance)));
        return new ForecastResult(points, deficitDate, BigDecimal.valueOf(troughBalance));
    }
}
