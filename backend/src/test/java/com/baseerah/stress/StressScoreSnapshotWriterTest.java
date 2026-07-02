package com.baseerah.stress;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.client.Client;
import com.baseerah.client.ClientRepository;
import com.baseerah.client.ClientService;
import com.baseerah.transaction.TransactionRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies {@link StressScoreSnapshotWriter}'s daily upsert against the local Liquibase-managed Postgres.
 * Skipped — not failed — when Postgres is unreachable (same convention as the seeder/persistence tests).
 *
 * <p>The clock is pinned to {@code 2026-07-02} so the trailing window deterministically covers the seeded
 * personas' most recent months. The test runs inside a transaction that rolls back, so it never pollutes
 * the seeded database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("com.baseerah.stress.StressScoreSnapshotWriterTest#postgresReachable")
@Transactional
class StressScoreSnapshotWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 2);
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-07-02T12:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private StressScoreCalculator calculator;
    @Autowired
    private StressScoreRepository stressScoreRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ClientRepository clientRepository;

    @Test
    void writesExactlyOneSnapshotPerClientPerDayAndUpdatesOnRerun() {
        StressScoreSnapshotWriter writer = new StressScoreSnapshotWriter(
                calculator, stressScoreRepository, transactionRepository, clientService, fixedClock);

        Client family = clientRepository.findByExternalId("client_001_family").orElseThrow();
        UUID clientId = family.getId();

        // First run creates the day's snapshot.
        StressScore first = writer.writeSnapshot(clientId.toString());
        assertThat(first.getAsOfDate()).isEqualTo(TODAY);
        assertThat(first.getScore()).isBetween(0, 100);
        assertThat(first.getZone()).isEqualTo(Zone.forScore(first.getScore()));
        assertThat(first.getSpendingVelocity()).isNotNull();
        assertThat(first.getIncomeConsistency()).isNotNull();
        assertThat(first.getLiabilityRatio()).isNotNull();

        long afterFirst = stressScoreRepository.findAll().stream()
                .filter(s -> s.getClient().getId().equals(clientId) && s.getAsOfDate().equals(TODAY))
                .count();
        assertThat(afterFirst).as("exactly one snapshot for the client on TODAY").isEqualTo(1);

        // Second run on the same day updates the same row rather than inserting a duplicate.
        StressScore second = writer.writeSnapshot(clientId.toString());
        assertThat(second.getId()).as("same row is reused (upsert, not insert)").isEqualTo(first.getId());

        long afterSecond = stressScoreRepository.findAll().stream()
                .filter(s -> s.getClient().getId().equals(clientId) && s.getAsOfDate().equals(TODAY))
                .count();
        assertThat(afterSecond).as("still exactly one snapshot after re-running").isEqualTo(1);
    }

    /** Fast TCP probe so the suite skips cleanly when the local Postgres is not up. */
    @SuppressWarnings("unused")
    static boolean postgresReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
