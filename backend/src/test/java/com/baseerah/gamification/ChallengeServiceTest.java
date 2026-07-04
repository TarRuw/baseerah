package com.baseerah.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.gamification.ChallengeService.ChallengeSpec;
import com.baseerah.seed.dto.SeedEnvelope;
import com.baseerah.seed.dto.SeedTransaction;
import com.baseerah.transaction.Category;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link ChallengeService#detect} over the real {@code data-mocks/} personas — no Spring,
 * no database, no clock (mirrors {@code StressScoreCalculatorTest}). It pins the load-bearing property of the
 * anomaly heuristic (DESIGN §5.6): challenges are derived from categories the persona <em>actually</em>
 * spends in, with telemetry-sized targets — never prototype constants.
 *
 * <p>The student persona ({@code client_004_student}) is a heavy small-ticket {@code RESTAURANTS_DINING}
 * spender, so it must surface a dining-triggered goal and — via the retrospective WELCOME challenge — at
 * least one already-complete ({@code pct == 100}) goal, which is what makes the Step 5.3 claim flow demoable.
 */
class ChallengeServiceTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void studentChallengesAreDerivedFromRealCategoryAnomalies() {
        List<Transaction> window = loadPersona("client_004_student");
        Set<String> spentCategories = window.stream()
                .filter(t -> t.getDirection() == Direction.DEBIT)
                .map(t -> t.resolveCategory().name())
                .collect(java.util.stream.Collectors.toSet());

        List<ChallengeSpec> specs = ChallengeService.detect(window);

        assertThat(specs).as("student has a discretionary habit → challenges generated").isNotEmpty();

        // Every generated goal's trigger must be a category the persona actually spends in — not invented.
        assertThat(specs)
                .allSatisfy(spec -> assertThat(spentCategories)
                        .as("trigger %s is a real observed category", spec.categoryTrigger())
                        .contains(spec.categoryTrigger()));

        // The student's dominant habit is dining, so a dining-triggered goal must exist.
        assertThat(specs).anySatisfy(spec ->
                assertThat(spec.categoryTrigger()).isEqualTo(Category.RESTAURANTS_DINING.name()));

        // Targets are sized from telemetry (positive), pct is always clamped to [0,100].
        assertThat(specs).allSatisfy(spec -> {
            assertThat(spec.targetValue()).as("%s target sized from telemetry", spec.code()).isPositive();
            assertThat(spec.pct()).as("%s pct clamped", spec.code()).isBetween(0, 100);
            assertThat(spec.rewardPoints()).as("%s awards points", spec.code()).isPositive();
        });

        // A completed, unclaimed goal exists (the WELCOME starter) — the demoable claim.
        assertThat(specs).anySatisfy(spec -> assertThat(spec.pct()).isEqualTo(100));
    }

    @Test
    void detectReturnsEmptyForAWindowWithNoDiscretionaryHabit() {
        assertThat(ChallengeService.detect(List.of()))
                .as("no transactions → no honestly-derivable challenges").isEmpty();
    }

    // ── Load a persona's transactions straight from the data-mocks source file ───────────────────────

    private List<Transaction> loadPersona(String externalId) {
        Path file = resolveDataMocksDir().resolve(externalId + "_6_months_data.json");
        SeedEnvelope envelope = read(file);
        return envelope.data().transactions().stream()
                .filter(t -> t.bookingDateTime() != null && !t.bookingDateTime().isBlank())
                .map(this::toTransaction)
                .toList();
    }

    private Transaction toTransaction(SeedTransaction tx) {
        return new Transaction(
                null, // account is irrelevant to the pure detector
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
