package com.baseerah.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web-layer coverage for the Step 6.2 Bank Portal endpoints, driven against the live Liquibase-managed
 * PostgreSQL seeded by {@code MockDataSeeder} + {@code BankApplicantSeeder}. Skipped — not failed — when
 * Postgres is unreachable on {@code localhost:5432}, matching the other DB-gated controller suites.
 *
 * <p>{@code @Transactional}: MockMvc dispatches the controller in-thread, so the decision write and the
 * risk-policy update join the test transaction and roll back at the end — the assertions still observe the
 * change, but the seeded queue and the singleton policy are never mutated across runs.
 *
 * <p>Applicants are resolved by their stable {@code seedKey} (not a fragile ordinal), so the tests target the
 * known bands: {@code APP-VIP-EXPANSION} is linked + underwritten (a full report), {@code APP-SYN-RENOVATION}
 * is a synthetic filler with no telemetry (report → 409).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.baseerah.bank.BankControllerTest#postgresReachable")
class BankControllerTest {

    /** Report generation must stay under the DESIGN §9 NFR. */
    private static final long REPORT_BUDGET_MS = 2500;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    private UUID applicationId(String seedKey) {
        return loanApplicationRepository.findBySeedKey(seedKey)
                .orElseThrow(() -> new AssertionError("applicant not seeded: " + seedKey))
                .getId();
    }

    @Test
    void applicantsReturnsEnvelopedQueue() throws Exception {
        mockMvc.perform(get("/api/v1/bank/applicants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data", Matchers.not(Matchers.empty())))
                // The VIP applicant carries name, initials, purpose, amount and an already-computed risk badge.
                .andExpect(jsonPath("$.data[?(@.applicantName == 'Khalid Al-Otaibi')].initials",
                        Matchers.contains("KO")))
                .andExpect(jsonPath("$.data[?(@.applicantName == 'Khalid Al-Otaibi')].purpose",
                        Matchers.contains("SME expansion facility")))
                .andExpect(jsonPath("$.data[?(@.applicantName == 'Khalid Al-Otaibi')].amount",
                        Matchers.contains(Matchers.greaterThan(0.0))))
                .andExpect(jsonPath("$.data[?(@.applicantName == 'Khalid Al-Otaibi')].verdict",
                        Matchers.contains("OK")));
    }

    @Test
    void reportReturnsFullEnvelopedReportUnderBudget() throws Exception {
        UUID vipId = applicationId("APP-VIP-EXPANSION");

        long startNanos = System.nanoTime();
        mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", vipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.staminaScore").isNumber())
                .andExpect(jsonPath("$.data.forecastDti").isNumber())
                .andExpect(jsonPath("$.data.incomeStability").isNumber())
                .andExpect(jsonPath("$.data.defaultProb12mo").isNumber())
                .andExpect(jsonPath("$.data.verdict").value("OK"))
                .andExpect(jsonPath("$.data.riskTier").value("A"))
                // 12-month cash-flow chart: a non-empty, at-most-12-point month-end series with date + balance.
                .andExpect(jsonPath("$.data.cashFlow", Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.data.cashFlow.length()", Matchers.lessThanOrEqualTo(12)))
                .andExpect(jsonPath("$.data.cashFlow[0].date").isString())
                .andExpect(jsonPath("$.data.cashFlow[0].balance").isNumber());
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(elapsedMs).as("report generation under the DESIGN §9 NFR").isLessThan(REPORT_BUDGET_MS);
    }

    @Test
    void reportUnknownIdReturns404ErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void decisionPersistsAndEchoesUpdatedApplicant() throws Exception {
        UUID familyId = applicationId("APP-FAMILY-AUTO");

        // Case-insensitive per the Decision enum: "approve" is accepted and normalised.
        mockMvc.perform(post("/api/v1/bank/applicants/{id}/decision", familyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.decision").value("APPROVE"));

        // Re-fetch reflects the persisted decision.
        assertThat(loanApplicationRepository.findById(familyId).orElseThrow().getDecision())
                .isEqualTo(Decision.APPROVE);
    }

    @Test
    void invalidDecisionReturns400ValidationEnvelope() throws Exception {
        UUID familyId = applicationId("APP-FAMILY-AUTO");

        mockMvc.perform(post("/api/v1/bank/applicants/{id}/decision", familyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void syntheticApplicantReportReturns409ConflictEnvelope() throws Exception {
        UUID syntheticId = applicationId("APP-SYN-RENOVATION");

        mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", syntheticId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void portfolioReturnsEnvelopedKpisAndMonitoringRows() throws Exception {
        mockMvc.perform(get("/api/v1/bank/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                // 4 KPIs (the seed underwrites 5 linked applicants, so the active book is non-empty).
                .andExpect(jsonPath("$.data.activeFacilities").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.avgStamina").isNumber())
                .andExpect(jsonPath("$.data.nplRate").isNumber())
                // Screening removes the worst risk, so the screened book's NPL is at or below the unscreened
                // baseline → the delta is never positive.
                .andExpect(jsonPath("$.data.nplBaselineDelta").value(Matchers.lessThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.data.atRiskAccounts").isNumber())
                // Monitoring rows carry borrower, facility, health, trend and status.
                .andExpect(jsonPath("$.data.monitoring", Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.data.monitoring[0].borrower").isString())
                .andExpect(jsonPath("$.data.monitoring[0].facility").isString())
                .andExpect(jsonPath("$.data.monitoring[0].health").isNumber())
                .andExpect(jsonPath("$.data.monitoring[0].trend").isString())
                .andExpect(jsonPath("$.data.monitoring[0].status").isString());
    }

    @Test
    void riskPolicyPutThenGetRoundTrips() throws Exception {
        String body = "{"
                + "\"staminaFloor\":55,"
                + "\"autoDeclineThreshold\":80,"
                + "\"ndmoResidency\":false,"
                + "\"tokenization\":false,"
                + "\"samaLastSync\":\"2026-01-15T10:30:00Z\"}";

        // PUT returns the persisted policy...
        mockMvc.perform(put("/api/v1/bank/risk-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.staminaFloor").value(55))
                .andExpect(jsonPath("$.data.autoDeclineThreshold").value(80))
                .andExpect(jsonPath("$.data.ndmoResidency").value(false))
                .andExpect(jsonPath("$.data.tokenization").value(false))
                .andExpect(jsonPath("$.data.samaLastSync").value("2026-01-15T10:30:00Z"));

        // ...and a subsequent GET reflects every written field.
        mockMvc.perform(get("/api/v1/bank/risk-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staminaFloor").value(55))
                .andExpect(jsonPath("$.data.autoDeclineThreshold").value(80))
                .andExpect(jsonPath("$.data.ndmoResidency").value(false))
                .andExpect(jsonPath("$.data.tokenization").value(false))
                .andExpect(jsonPath("$.data.samaLastSync").value("2026-01-15T10:30:00Z"));
    }

    @Test
    void riskPolicyPutRejectsOutOfRangeValues() throws Exception {
        // staminaFloor above 100 violates @Max → the shared validation envelope.
        String body = "{"
                + "\"staminaFloor\":150,"
                + "\"autoDeclineThreshold\":80,"
                + "\"ndmoResidency\":true,"
                + "\"tokenization\":true,"
                + "\"samaLastSync\":null}";

        mockMvc.perform(put("/api/v1/bank/risk-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
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
