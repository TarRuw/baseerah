package com.baseerah.rescue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseerah.client.ClientRepository;
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
 * Web-layer coverage for the Step 4.2 Smart Rescue endpoints, driven against the live Liquibase-managed
 * PostgreSQL seeded by {@code MockDataSeeder}. Skipped — not failed — when Postgres is unreachable on
 * {@code localhost:5432}, matching {@code LoanControllerTest}/{@code ForecastControllerTest}.
 *
 * <p>The class is {@code @Transactional}: MockMvc dispatches the controller in-thread, so the confirm
 * endpoint's {@code rescue_events} write joins the test transaction and rolls back at the end — the
 * persistence assertion still sees the row, but the append-only log is never polluted across runs.
 *
 * <p>Asserts the <em>mechanics</em> Smart Rescue must get right rather than a 15-day alert on the freelancer:
 * on the frozen mock their deficit is real but years out (Step 4.1 handoff, user-approved faithful-engine
 * realignment), so the endpoint honestly reports a deficit with two options while the alert flag agrees with
 * the true lead time. The healthy persona returns the explicit no-deficit state, not a 4xx.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.baseerah.rescue.RescueControllerTest#postgresReachable")
class RescueControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private RescueEventRepository rescueEventRepository;

    private UUID seededClientId(String externalId) {
        return clientRepository.findByExternalId(externalId)
                .orElseThrow(() -> new AssertionError("persona not seeded: " + externalId))
                .getId();
    }

    @Test
    void deficitPersonaReturnsShortfallAndTwoOptions() throws Exception {
        UUID freelancerId = seededClientId("client_003_freelancer");

        mockMvc.perform(get("/api/v1/clients/{id}/rescue", freelancerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.hasDeficit").value(true))
                .andExpect(jsonPath("$.data.shortfall").value(Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data.deficitInDays").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.options", Matchers.hasSize(2)))
                // Exactly one MURABAHA (with a repayment term) and one LIQUIDATE (no term).
                .andExpect(jsonPath("$.data.options[?(@.type == 'MURABAHA')].term",
                        Matchers.contains(Matchers.greaterThan(0))))
                .andExpect(jsonPath("$.data.options[?(@.type == 'LIQUIDATE')].term",
                        Matchers.contains(Matchers.nullValue())))
                // amount is rounded to a whole SAR-100 unit → serialized as an integer JSON number.
                .andExpect(jsonPath("$.data.options[?(@.type == 'MURABAHA')].amount",
                        Matchers.contains(Matchers.greaterThan(0))));
    }

    @Test
    void healthyPersonaReturnsNoDeficitState() throws Exception {
        UUID familyId = seededClientId("client_001_family");

        mockMvc.perform(get("/api/v1/clients/{id}/rescue", familyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.hasDeficit").value(false))
                .andExpect(jsonPath("$.data.deficitInDays").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.shortfall").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.options", Matchers.hasSize(0)));
    }

    @Test
    void confirmRecoversScoreAndPersistsOneEvent() throws Exception {
        UUID freelancerId = seededClientId("client_003_freelancer");
        long before = rescueEventRepository.count();

        mockMvc.perform(post("/api/v1/clients/{id}/rescue/confirm", freelancerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"LIQUIDATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.scoreBefore").isNumber())
                .andExpect(jsonPath("$.data.scoreAfter").isNumber())
                .andExpect(jsonPath("$.data.scoreAfter",
                        Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.message").isString());

        // Exactly one new rescue_events row for this client, carrying the chosen option and a real recovery.
        assertThat(rescueEventRepository.count()).as("one rescue_events row persisted").isEqualTo(before + 1);
        RescueEvent event = rescueEventRepository.findFirstByClient_IdOrderByCreatedAtDesc(freelancerId)
                .orElseThrow(() -> new AssertionError("no rescue_events row written for the freelancer"));
        assertThat(event.getOptionChosen()).isEqualTo(RescueOptionType.LIQUIDATE);
        assertThat(event.getScoreAfter()).as("score recovers on confirm")
                .isGreaterThan(event.getScoreBefore());
    }

    @Test
    void unknownIdReturns404ErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/rescue", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void invalidOptionReturns400ValidationEnvelope() throws Exception {
        UUID freelancerId = seededClientId("client_003_freelancer");

        mockMvc.perform(post("/api/v1/clients/{id}/rescue/confirm", freelancerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"BITCOIN\"}"))
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
