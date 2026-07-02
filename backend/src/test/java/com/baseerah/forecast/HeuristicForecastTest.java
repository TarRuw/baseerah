package com.baseerah.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.forecast.ForecastEngine.ForecastPoint;
import com.baseerah.forecast.ForecastEngine.ForecastResult;
import com.baseerah.seed.dto.SeedEnvelope;
import com.baseerah.seed.dto.SeedTransaction;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link HeuristicForecast#forecast} over two real personas — no Spring, no database.
 * Reads the read-only {@code data-mocks/} source files directly (same pattern as
 * {@code StressScoreCalculatorTest}) and projects each persona's trailing window, so the test pins the
 * projection's behaviour independently of seeding and the clock.
 *
 * <p><strong>What this asserts (and why it is not a "freelancer deficit" test).</strong> DESIGN §11 casts
 * the freelancer as the Smart-Rescue deficit demo, but their seeded balance is high and <em>rising</em>
 * (~185k of irregular {@code INFLOW_BUSINESS} over six months): a faithful projection from the latest
 * {@code closing_balance} therefore shows <em>no</em> near-term deficit — the deficit is a Phase-4 what-if
 * scenario, not a property of the base forecast (see the step-03-01 handoff). What the data does support,
 * and what the engine must get right, is the qualitative split the design cares about: the salaried family
 * has a <em>recurring</em> inflow so its balance recovers and never deficits, whereas the freelancer's
 * income is irregular (never detected as recurring) so its projection only declines. That contrast is the
 * load-bearing assertion below.
 */
class HeuristicForecastTest {

    /** forecast() uses neither the repository nor the clock, so a null-repo engine is sufficient here. */
    private final HeuristicForecast engine = new HeuristicForecast(null);

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 2);
    private static final int HORIZON_DAYS = 60;

    @Test
    void seriesIsWellFormedForBothPersonas() {
        for (String persona : List.of("client_001_family", "client_003_freelancer")) {
            ForecastResult result = engine.forecast(trailingWindow(persona), TODAY, HORIZON_DAYS);
            List<ForecastPoint> points = result.points();

            assertThat(points).as("%s produces a non-empty series", persona).isNotEmpty();
            assertThat(points).as("%s series spans [today, today+horizon] inclusive", persona)
                    .hasSize(HORIZON_DAYS + 1);
            assertThat(points.get(0).date()).as("%s series starts at today", persona).isEqualTo(TODAY);
            assertThat(points.get(points.size() - 1).date()).as("%s series ends at the horizon", persona)
                    .isEqualTo(TODAY.plusDays(HORIZON_DAYS));
            assertThat(points).as("%s points are strictly ordered by date", persona)
                    .isSortedAccordingTo(Comparator.comparing(ForecastPoint::date));

            BigDecimal expectedMin = points.stream().map(ForecastPoint::projectedBalance)
                    .min(Comparator.naturalOrder()).orElseThrow();
            assertThat(result.minProjectedBalance()).as("%s minProjectedBalance is the series minimum", persona)
                    .isEqualByComparingTo(expectedMin);
        }
    }

    @Test
    void stableFamilyRecoversWithNoDeficit() {
        ForecastResult family = engine.forecast(trailingWindow("client_001_family"), TODAY, HORIZON_DAYS);

        assertThat(family.deficitDate())
                .as("stable salaried family (recurring salary detected) never deficits within the horizon")
                .isNull();
        assertThat(endBalance(family))
                .as("recurring salary inflow lifts the family's projected balance above where it started")
                .isGreaterThan(startBalance(family));
    }

    @Test
    void irregularFreelancerOnlyDeclines() {
        ForecastResult freelancer =
                engine.forecast(trailingWindow("client_003_freelancer"), TODAY, HORIZON_DAYS);

        // The freelancer's income is irregular, so no recurring inflow is detected; with only burn +
        // recurring outflows the projection declines. Their large accumulated buffer means it does not
        // cross zero within the horizon — the deficit narrative belongs to a Phase-4 scenario, not here.
        assertThat(freelancer.deficitDate())
                .as("freelancer's accumulated buffer prevents a base-forecast deficit within the horizon")
                .isNull();
        assertThat(endBalance(freelancer))
                .as("with no recurring inflow, the freelancer's projection only declines")
                .isLessThan(startBalance(freelancer));
    }

    private static BigDecimal startBalance(ForecastResult result) {
        return result.points().get(0).projectedBalance();
    }

    private static BigDecimal endBalance(ForecastResult result) {
        return result.points().get(result.points().size() - 1).projectedBalance();
    }

    // ── Load a persona's trailing-window transactions straight from the data-mocks source file ──────────

    /** The persona's dated transactions within {@link HeuristicForecast#WINDOW_DAYS} of {@link #TODAY}. */
    private List<Transaction> trailingWindow(String externalId) {
        LocalDate from = TODAY.minusDays(HeuristicForecast.WINDOW_DAYS);
        return loadPersona(externalId).stream()
                .filter(tx -> {
                    LocalDate date = tx.getBookingDate().atZone(ZoneOffset.UTC).toLocalDate();
                    return !date.isBefore(from) && !date.isAfter(TODAY);
                })
                .toList();
    }

    /** Maps a {@code data-mocks/<persona>_6_months_data.json} file to detached {@link Transaction}s. */
    private List<Transaction> loadPersona(String externalId) {
        Path file = resolveDataMocksDir().resolve(externalId + "_6_months_data.json");
        SeedEnvelope envelope = read(file);
        return envelope.data().transactions().stream()
                // Projection requires a booking date; skip the freelancer's date-less income placeholders
                // (irregular inflows the seeder imputes for the DB — never recurring, so they do not affect
                // the projection, which models recurring flows plus discretionary burn).
                .filter(t -> t.bookingDateTime() != null && !t.bookingDateTime().isBlank())
                .map(this::toTransaction)
                .toList();
    }

    private Transaction toTransaction(SeedTransaction tx) {
        return new Transaction(
                null, // account is irrelevant to the pure projection
                tx.transactionId(),
                Direction.valueOf(tx.creditDebitIndicator()),
                tx.amount().amount(),
                tx.amount().currency(),
                tx.transactionInformation(),
                tx.insights() == null ? null : tx.insights().descriptionCleansed(),
                tx.insights() == null ? null : tx.insights().category(),
                tx.insights() == null ? null : tx.insights().categoryConfidence(),
                Instant.parse(tx.bookingDateTime()),
                tx.balance() == null ? null : tx.balance().amount().amount());
    }

    private SeedEnvelope read(Path file) {
        try {
            return mapper.readValue(Files.readString(file), SeedEnvelope.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read persona file " + file, e);
        }
    }

    /** Walk up from the working directory to the repo-root {@code data-mocks/} dir (same as the seeder test). */
    private Path resolveDataMocksDir() {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path candidate = base.resolve("data-mocks");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("data-mocks directory not found from working dir");
    }
}
