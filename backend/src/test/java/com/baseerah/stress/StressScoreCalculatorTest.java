package com.baseerah.stress;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.seed.dto.SeedEnvelope;
import com.baseerah.seed.dto.SeedTransaction;
import com.baseerah.transaction.Direction;
import com.baseerah.transaction.Transaction;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link StressScoreCalculator} over two real personas — no Spring, no database. Reads
 * the read-only {@code data-mocks/} source files directly and feeds each persona's transactions to the
 * calculator, so the test pins the algorithm's behaviour independently of seeding and the clock.
 *
 * <p>The load-bearing assertion is the <em>relative ordering</em> required by DESIGN §5.1: the stable
 * salaried family ({@code client_001_family}) must score healthier than the irregular-income freelancer
 * ({@code client_003_freelancer}). This is what constrains the normalisation curves the engineer chooses.
 */
class StressScoreCalculatorTest {

    private final StressScoreCalculator calculator = new StressScoreCalculator();

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void scoresAreInRangeWithConsistentZones() {
        for (String persona : List.of("client_001_family", "client_003_freelancer")) {
            StressScoreResult result = calculator.calculate(loadPersona(persona));

            assertThat(result.score()).as("%s score in [0,100]", persona).isBetween(0, 100);
            assertThat(result.zone()).as("%s zone matches its score", persona)
                    .isEqualTo(Zone.forScore(result.score()));
            assertThat(result.velocitySubScore()).as("%s velocity sub-score in [0,1]", persona)
                    .isBetween(0.0, 1.0);
            assertThat(result.consistencySubScore()).as("%s consistency sub-score in [0,1]", persona)
                    .isBetween(0.0, 1.0);
            assertThat(result.liabilitySubScore()).as("%s liability sub-score in [0,1]", persona)
                    .isBetween(0.0, 1.0);
        }
    }

    @Test
    void stableFamilyScoresHealthierThanIrregularFreelancer() {
        StressScoreResult family = calculator.calculate(loadPersona("client_001_family"));
        StressScoreResult freelancer = calculator.calculate(loadPersona("client_003_freelancer"));

        assertThat(family.score())
                .as("stable salaried family should be healthier than the irregular freelancer")
                .isGreaterThan(freelancer.score());
    }

    // ── Load a persona's transactions straight from the data-mocks source file ───────────────────────

    /** Maps a {@code data-mocks/<persona>_6_months_data.json} file to detached {@link Transaction}s. */
    private List<Transaction> loadPersona(String externalId) {
        Path file = resolveDataMocksDir().resolve(externalId + "_6_months_data.json");
        SeedEnvelope envelope = read(file);
        return envelope.data().transactions().stream()
                // Calculator requires a booking date; skip the freelancer's date-less placeholders
                // (the seeder imputes these for the DB — irrelevant to the relative-ordering assertion).
                .filter(t -> t.bookingDateTime() != null && !t.bookingDateTime().isBlank())
                .map(this::toTransaction)
                .toList();
    }

    private Transaction toTransaction(SeedTransaction tx) {
        return new Transaction(
                null, // account is irrelevant to the pure calculator
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
