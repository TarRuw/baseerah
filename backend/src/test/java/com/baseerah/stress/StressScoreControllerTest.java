package com.baseerah.stress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseerah.client.ClientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web-layer coverage for the Step 2.2 stress-score endpoint, driven against the live Liquibase-managed
 * PostgreSQL seeded by {@code MockDataSeeder}. Skipped — not failed — when Postgres is unreachable on
 * {@code localhost:5432}, matching {@code CoreEndpointsTest}/{@code StressScoreSnapshotWriterTest}.
 *
 * <p>{@code @Transactional} so the endpoint's lazy first-compute (there is no snapshot at boot) is rolled
 * back and never pollutes the seeded database — the same hygiene as the snapshot-writer test.
 *
 * <p>Verifies: the enveloped payload shape, that {@code zone} bands {@code score} per §5.1 and {@code color}
 * matches the §8 zone hex, the 2.5 s NFR for the large-volume persona {@code client_002_tech}, and the
 * shared 404 error envelope for an unknown id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.baseerah.stress.StressScoreControllerTest#postgresReachable")
class StressScoreControllerTest {

    /** Zone → gauge hex (DESIGN.md §8) — the served colour must agree with the banded zone. */
    private static final Map<String, String> ZONE_HEX = Map.of(
            "OPTIMAL", "#1D9E63",
            "WARNING", "#E5A63A",
            "CRITICAL", "#E0574F");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID seededClientId(String externalId) {
        return clientRepository.findByExternalId(externalId)
                .orElseThrow(() -> new AssertionError("persona not seeded: " + externalId))
                .getId();
    }

    @Test
    void seededClientReturnsEnvelopeWithZoneMatchingColour() throws Exception {
        UUID clientId = seededClientId("client_001_family");

        MvcResult result = mockMvc.perform(get("/api/v1/clients/{id}/stress-score", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.score").isNumber())
                .andExpect(jsonPath("$.data.zone").exists())
                .andExpect(jsonPath("$.data.color").exists())
                .andExpect(jsonPath("$.data.spendingVelocity").isNumber())
                .andExpect(jsonPath("$.data.incomeConsistency").isNumber())
                .andExpect(jsonPath("$.data.liabilityRatio").isNumber())
                .andExpect(jsonPath("$.data.asOfDate").exists())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        int score = data.path("score").asInt();
        String zone = data.path("zone").asText();
        String color = data.path("color").asText();

        // Zone banding matches the §5.1 thresholds, and the colour matches the §8 zone hex.
        assertThat(score).isBetween(0, 100);
        assertThat(zone).isEqualTo(Zone.forScore(score).name());
        assertThat(color).isEqualTo(ZONE_HEX.get(zone));
    }

    @Test
    void largeVolumePersonaRespondsWithinBudget() throws Exception {
        UUID techId = seededClientId("client_002_tech_bro");

        long startNanos = System.nanoTime();
        mockMvc.perform(get("/api/v1/clients/{id}/stress-score", techId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.score").isNumber());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertThat(elapsed)
                .as("stress-score under the 2.5 s NFR for the large-volume persona (DESIGN.md §9)")
                .isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void unknownIdReturns404ErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/stress-score", UUID.randomUUID().toString()))
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
