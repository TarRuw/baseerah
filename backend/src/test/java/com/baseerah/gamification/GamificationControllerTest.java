package com.baseerah.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseerah.client.ClientRepository;
import com.baseerah.client.ClientService;
import com.baseerah.transaction.TransactionRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Predicate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web-layer coverage for the Step 5.2 gamification endpoints, driven against the live Liquibase-managed
 * PostgreSQL seeded by {@code MockDataSeeder}. Skipped — not failed — when Postgres is unreachable on
 * {@code localhost:5432}, matching {@code RescueControllerTest}.
 *
 * <p>The class is {@code @Transactional}: MockMvc dispatches the controller in-thread, so the claim's ledger
 * write joins the test transaction and rolls back at the end — the append-only ledger is never polluted
 * across runs. Challenges are (idempotently) regenerated in {@code @BeforeEach} with a <strong>fixed
 * clock</strong> anchored just after the mock-data window, so the student's completed "Mindful spender" goal
 * exists deterministically regardless of the machine date (the boot {@link ChallengeSeeder} using the system
 * clock is exercised by a real app boot).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.baseerah.gamification.GamificationControllerTest#postgresReachable")
class GamificationControllerTest {

    /** Anchors the trailing window inside the Jan–Jun 2026 mock data so detection is date-independent. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-30T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClientRepository clientRepository;
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

    private UUID studentId;

    @BeforeEach
    void generateStudentChallenges() {
        studentId = clientRepository.findByExternalId("client_004_student")
                .orElseThrow(() -> new AssertionError("student persona not seeded"))
                .getId();
        // Fixed-clock instance → deterministic detection window; joins (and rolls back with) the test tx.
        new ChallengeService(transactionRepository, clientService, challengeRepository,
                challengeProgressRepository, com.baseerah.common.Messages.forTests(), FIXED_CLOCK)
                .generateForClient(studentId);
    }

    private Challenge challengeMatching(Predicate<ChallengeProgress> progressTest) {
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
    void listReturnsEnvelopedChallengesWithProgressFields() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/challenges", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data", Matchers.hasSize(Matchers.greaterThan(0))))
                // Every item carries the full projection...
                .andExpect(jsonPath("$.data[*].id").exists())
                .andExpect(jsonPath("$.data[*].pct").exists())
                .andExpect(jsonPath("$.data[*].progressText").exists())
                // ...progress text is the SAR-labelled string (default locale → "SAR")...
                .andExpect(jsonPath("$.data[?(@.title == 'Mindful spender')].progressText",
                        Matchers.contains(Matchers.containsString("SAR"))))
                // ...and the seeded completed starter goal is claimable and not yet claimed.
                .andExpect(jsonPath("$.data[?(@.title == 'Mindful spender')].pct",
                        Matchers.contains(100)))
                .andExpect(jsonPath("$.data[?(@.title == 'Mindful spender')].claimable",
                        Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.title == 'Mindful spender')].claimed",
                        Matchers.contains(false)));
    }

    @Test
    void claimOnCompletedChallengeAwardsPointsAndFlipsClaimed() throws Exception {
        Challenge completed = challengeMatching(p -> p.isComplete() && !p.isClaimed());
        int reward = completed.getRewardPoints();

        mockMvc.perform(post("/api/v1/clients/{id}/challenges/{cid}/claim", studentId, completed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.points").value(reward)) // first claim → balance == reward
                .andExpect(jsonPath("$.data.riskTier").isString())
                .andExpect(jsonPath("$.data.challenge.id").value(completed.getId().toString()))
                .andExpect(jsonPath("$.data.challenge.claimed").value(true))
                .andExpect(jsonPath("$.data.challenge.claimable").value(false));

        assertThat(ledgerRowCount()).as("exactly one ledger row written").isEqualTo(1);
    }

    @Test
    void secondClaimOnSameChallengeIsRejectedWithConflictAndDoesNotChangeBalance() throws Exception {
        Challenge completed = challengeMatching(p -> p.isComplete() && !p.isClaimed());

        mockMvc.perform(post("/api/v1/clients/{id}/challenges/{cid}/claim", studentId, completed.getId()))
                .andExpect(status().isOk()); // first claim succeeds
        int rowsAfterFirst = ledgerRowCount();

        mockMvc.perform(post("/api/v1/clients/{id}/challenges/{cid}/claim", studentId, completed.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        assertThat(ledgerRowCount()).as("re-claim writes no second reward").isEqualTo(rowsAfterFirst);
    }

    @Test
    void claimOnIncompleteChallengeIsRejectedWithConflict() throws Exception {
        Challenge incomplete = challengeMatching(p -> !p.isComplete());
        int rowsBefore = ledgerRowCount();

        mockMvc.perform(post("/api/v1/clients/{id}/challenges/{cid}/claim", studentId, incomplete.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        assertThat(ledgerRowCount()).as("no reward written for an incomplete claim").isEqualTo(rowsBefore);
    }

    @Test
    void rewardsReturnsEnvelopedPointsAndTier() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/rewards", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.points").isNumber())
                .andExpect(jsonPath("$.data.riskTier").isString());
    }

    @Test
    void unknownClientReturns404ErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/challenges", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
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
