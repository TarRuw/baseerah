package com.baseerah.rescue;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.client.ClientRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies {@link RescueService} end-to-end against the live Liquibase-managed PostgreSQL seeded by
 * {@code MockDataSeeder}. Skipped — not failed — when Postgres is unreachable on {@code localhost:5432}
 * (same convention as {@code ForecastControllerTest}/{@code StressScoreSnapshotWriterTest}); the test runs
 * inside a transaction that rolls back, so the {@code rescue_events} it writes never pollute the DB.
 *
 * <p><strong>What this pins — and the Step 4.1 realignment (user-approved).</strong> The step's prose casts
 * {@code client_003_freelancer} as a 15-day-lead deficit alert, but on the frozen mock their base forecast
 * (irregular income, ~239k buffer) only crosses zero years out — see the Step 3.1 and 4.1 handoffs. Rather
 * than fabricate a near-term deficit with prototype constants (forbidden by the Global Rules) or edit the
 * read-only mock, the engine stays faithful and this test asserts the <em>mechanics</em> Smart Rescue must
 * get right: a real positive shortfall, a computed lead time, exactly two options, and a confirmed rescue
 * that recovers the score and logs one row. The 15-day alert flag is asserted to agree with the true lead
 * time (honestly {@code false} here), not asserted true.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("com.baseerah.rescue.RescueServiceTest#postgresReachable")
@Transactional
class RescueServiceTest {

    @Autowired
    private RescueService rescueService;
    @Autowired
    private RescueEventRepository rescueEventRepository;
    @Autowired
    private ClientRepository clientRepository;

    private UUID seededClientId(String externalId) {
        return clientRepository.findByExternalId(externalId)
                .orElseThrow(() -> new AssertionError("persona not seeded: " + externalId))
                .getId();
    }

    private RescueOption optionOfType(RescueAssessment assessment, RescueOptionType type) {
        return assessment.options().stream()
                .filter(o -> o.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " option in assessment"));
    }

    @Test
    void assessFreelancerReturnsDeficitWithTwoWellFormedOptions() {
        UUID freelancerId = seededClientId("client_003_freelancer");

        RescueAssessment assessment = rescueService.assess(freelancerId);

        assertThat(assessment.hasDeficit()).as("freelancer's declining projection reaches a deficit").isTrue();
        assertThat(assessment.predictedShortfall())
                .as("a positive SAR shortfall to bridge").isPositive();
        assertThat(assessment.deficitInDays()).as("a positive lead time to the deficit").isPositive();
        // Faithful engine: the freelancer's deficit is real but far out, so the 15-day alert honestly does
        // not fire. The flag must simply agree with the true lead time.
        assertThat(assessment.alertRaised())
                .as("alert flag agrees with the 15-day lead window")
                .isEqualTo(assessment.deficitInDays() <= RescueService.ALERT_LEAD_DAYS);

        assertThat(assessment.options()).as("exactly two bridge options").hasSize(2);
        RescueOption murabaha = optionOfType(assessment, RescueOptionType.MURABAHA);
        RescueOption liquidate = optionOfType(assessment, RescueOptionType.LIQUIDATE);
        assertThat(murabaha.amount()).as("murabaha covers the shortfall").isPositive();
        assertThat(murabaha.term()).as("murabaha carries a repayment term").isNotNull();
        assertThat(murabaha.term()).isPositive();
        assertThat(liquidate.amount()).as("liquidate covers the shortfall").isPositive();
        assertThat(liquidate.term()).as("liquidation has no repayment term").isNull();
    }

    @Test
    void assessHealthyFamilyReturnsNoDeficit() {
        UUID familyId = seededClientId("client_001_family");

        RescueAssessment assessment = rescueService.assess(familyId);

        // Recurring salary lifts the family's projection, so it never crosses zero — no deficit, no alert,
        // no options (mirrors the Step 3.2 family forecast assertion).
        assertThat(assessment.hasDeficit()).as("salaried family never deficits").isFalse();
        assertThat(assessment.alertRaised()).isFalse();
        assertThat(assessment.options()).isEmpty();
        assertThat(assessment.predictedShortfall()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    void confirmLiquidateRecoversScoreAndLogsExactlyOneEvent() {
        UUID freelancerId = seededClientId("client_003_freelancer");
        RescueAssessment assessment = rescueService.assess(freelancerId);
        RescueOption liquidate = optionOfType(assessment, RescueOptionType.LIQUIDATE);

        long before = rescueEventRepository.findAll().stream()
                .filter(e -> e.getClient().getId().equals(freelancerId)).count();

        RescueOutcome outcome = rescueService.confirm(freelancerId, liquidate);

        assertThat(outcome.scoreAfter())
                .as("bridge recovers the stress score").isGreaterThan(outcome.scoreBefore());

        long after = rescueEventRepository.findAll().stream()
                .filter(e -> e.getClient().getId().equals(freelancerId)).count();
        assertThat(after - before).as("exactly one rescue_events row written").isEqualTo(1);

        RescueEvent logged = rescueEventRepository
                .findFirstByClient_IdOrderByCreatedAtDesc(freelancerId).orElseThrow();
        assertThat(logged.getOptionChosen()).isEqualTo(RescueOptionType.LIQUIDATE);
        assertThat(logged.getScoreBefore()).isEqualTo(outcome.scoreBefore());
        assertThat(logged.getScoreAfter()).isEqualTo(outcome.scoreAfter());
        assertThat(logged.getPredictedShortfall()).isEqualByComparingTo(assessment.predictedShortfall());
        assertThat(logged.getDeficitInDays()).isEqualTo(assessment.deficitInDays());
    }

    @Test
    void liquidationRecoversMoreThanMurabaha() {
        UUID freelancerId = seededClientId("client_003_freelancer");
        RescueAssessment assessment = rescueService.assess(freelancerId);

        RescueOutcome murabaha =
                rescueService.confirm(freelancerId, optionOfType(assessment, RescueOptionType.MURABAHA));
        RescueOutcome liquidate =
                rescueService.confirm(freelancerId, optionOfType(assessment, RescueOptionType.LIQUIDATE));

        // Same starting score; a cost-free liquidation recovers strictly more than financed murabaha (§5.4).
        assertThat(murabaha.scoreBefore()).isEqualTo(liquidate.scoreBefore());
        assertThat(liquidate.scoreAfter()).isGreaterThan(murabaha.scoreAfter());
        assertThat(murabaha.scoreAfter()).isGreaterThan(murabaha.scoreBefore());
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
