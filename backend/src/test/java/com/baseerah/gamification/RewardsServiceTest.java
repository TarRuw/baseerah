package com.baseerah.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.baseerah.client.ClientRepository;
import com.baseerah.client.ClientService;
import com.baseerah.common.ConflictException;
import com.baseerah.gamification.RewardsService.ClaimResult;
import com.baseerah.transaction.TransactionRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies {@link RewardsService}'s claim flow end-to-end against the live Liquibase-managed PostgreSQL
 * seeded by {@code MockDataSeeder}. Skipped — not failed — when Postgres is unreachable on
 * {@code localhost:5432} (same convention as {@code RescueServiceTest}); the test runs inside a transaction
 * that rolls back, so the challenges/ledger rows it writes never persist.
 *
 * <p>Challenges are generated in-test with a <strong>fixed clock</strong> anchored just after the mock data
 * window, so the student's dining habit is detected deterministically regardless of the machine date — the
 * boot {@link ChallengeSeeder} (which uses the system clock) is exercised separately by a real app boot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("com.baseerah.gamification.RewardsServiceTest#postgresReachable")
@Transactional
class RewardsServiceTest {

    /** Anchors the trailing window inside the Jan–Jun 2026 mock data so detection is date-independent. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private RewardsService rewardsService;
    @Autowired
    private ChallengeRepository challengeRepository;
    @Autowired
    private ChallengeProgressRepository challengeProgressRepository;
    @Autowired
    private RewardsLedgerRepository rewardsLedgerRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ClientService clientService;
    @Autowired
    private ClientRepository clientRepository;

    private UUID studentId;

    @BeforeEach
    void generateStudentChallenges() {
        studentId = clientRepository.findByExternalId("client_004_student")
                .orElseThrow(() -> new AssertionError("student persona not seeded"))
                .getId();
        // Fixed-clock instance → deterministic detection window; joins the test's transaction.
        ChallengeService fixedClockService = new ChallengeService(transactionRepository, clientService,
                challengeRepository, challengeProgressRepository, FIXED_CLOCK);
        fixedClockService.generateForClient(studentId);
    }

    private Challenge challengeMatching(java.util.function.Predicate<ChallengeProgress> progressTest) {
        return challengeRepository.findByClient_Id(studentId).stream()
                .filter(c -> challengeProgressRepository.findByChallenge_Id(c.getId())
                        .filter(progressTest).isPresent())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no challenge matching the required progress state"));
    }

    private int ledgerRowCount() {
        return rewardsLedgerRepository.findByClient_Id(studentId).size();
    }

    @Test
    void claimingCompletedChallengeAwardsPointsWritesLedgerAndUpdatesTier() {
        Challenge completed = challengeMatching(p -> p.isComplete() && !p.isClaimed());
        int balanceBefore = rewardsService.summaryFor(studentId).balance();
        int rowsBefore = ledgerRowCount();

        ClaimResult result = rewardsService.claimChallenge(studentId, completed.getId());

        assertThat(result.awardedPoints()).as("awards the challenge's reward points")
                .isEqualTo(completed.getRewardPoints());
        assertThat(result.balance()).as("balance increases by exactly the reward")
                .isEqualTo(balanceBefore + completed.getRewardPoints());
        assertThat(ledgerRowCount()).as("exactly one ledger row written").isEqualTo(rowsBefore + 1);

        ChallengeProgress progress = challengeProgressRepository.findByChallenge_Id(completed.getId())
                .orElseThrow();
        assertThat(progress.isClaimed()).as("progress flipped to claimed").isTrue();
        assertThat(progress.getClaimedAt()).as("claimed timestamp set").isNotNull();

        // Tier is always consistent with the recomputed balance.
        assertThat(result.tier()).isEqualTo(RewardTier.forBalance(result.balance()));
        assertThat(rewardsService.summaryFor(studentId).tier()).isEqualTo(result.tier());
    }

    @Test
    void claimingIncompleteChallengeIsRejectedAndWritesNoLedgerRow() {
        Challenge incomplete = challengeMatching(p -> !p.isComplete());
        int rowsBefore = ledgerRowCount();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> rewardsService.claimChallenge(studentId, incomplete.getId()));

        assertThat(ledgerRowCount()).as("no reward written for an incomplete claim").isEqualTo(rowsBefore);
    }

    @Test
    void reclaimingAnAlreadyClaimedChallengeIsRejectedAndWritesNoLedgerRow() {
        Challenge completed = challengeMatching(p -> p.isComplete() && !p.isClaimed());
        rewardsService.claimChallenge(studentId, completed.getId()); // first claim succeeds
        int rowsAfterFirst = ledgerRowCount();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> rewardsService.claimChallenge(studentId, completed.getId()));

        assertThat(ledgerRowCount()).as("re-claim writes no second reward").isEqualTo(rowsAfterFirst);
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
