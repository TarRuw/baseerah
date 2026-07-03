package com.baseerah.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseerah.client.ClientRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer coverage for the Step 3.2 forecast endpoint, driven against the live Liquibase-managed
 * PostgreSQL seeded by {@code MockDataSeeder}. Skipped — not failed — when Postgres is unreachable on
 * {@code localhost:5432}, matching {@code StressScoreControllerTest}/{@code CoreEndpointsTest}.
 *
 * <p>Verifies: the enveloped {@code {points,deficitDate,minProjectedBalance,trend}} shape and a series of
 * {@code horizonDays + 1} points; that the 3/6/12-month (90/180/365) horizons flow through the same param;
 * the 2.5 s NFR for the large-volume persona {@code client_002_tech_bro} (DESIGN.md §9); the shared 404 for
 * an unknown id and 400 for an out-of-range horizon.
 *
 * <p><strong>Persona deficit contrast (Step 3.1 deviation, user-approved).</strong> On the frozen mock
 * data a faithful base forecast produces <em>no</em> deficit within the home horizon for either the family
 * (recurring salary lifts the balance) or the freelancer (a ~239k accumulated buffer that only declines
 * slowly) — see the Step 3.1 handoff. So both assert {@code deficitDate == null} here; the freelancer's
 * headline deficit is a Phase-4 what-if scenario layered on top of this base projection, not a property of
 * it. The chart's deficit-marker rendering is still exercised by its own widget-level check on the client.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.baseerah.forecast.ForecastControllerTest#postgresReachable")
class ForecastControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    private UUID seededClientId(String externalId) {
        return clientRepository.findByExternalId(externalId)
                .orElseThrow(() -> new AssertionError("persona not seeded: " + externalId))
                .getId();
    }

    @Test
    void seededClientReturnsForecastEnvelope() throws Exception {
        UUID clientId = seededClientId("client_001_family");

        mockMvc.perform(get("/api/v1/clients/{id}/forecast", clientId).param("horizonDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                // 30-day horizon → today..today+30 inclusive = 31 points, each {date, balance}.
                .andExpect(jsonPath("$.data.points.length()").value(31))
                .andExpect(jsonPath("$.data.points[0].date").exists())
                .andExpect(jsonPath("$.data.points[0].balance").isNumber())
                .andExpect(jsonPath("$.data.minProjectedBalance").isNumber())
                // trend is one of the three bands consumed by the chart.
                .andExpect(jsonPath("$.data.trend").value(org.hamcrest.Matchers.in(
                        new String[] {"UP", "FLAT", "DOWN"})))
                // Family: recurring salary lifts the balance → no base-forecast deficit (Step 3.1 handoff).
                .andExpect(jsonPath("$.data.deficitDate").doesNotExist());
    }

    @Test
    void freelancerHasNoBaseForecastDeficitWithinHomeHorizon() throws Exception {
        // Step 3.1 deviation (user-approved): the freelancer's large accumulated buffer means the faithful
        // base forecast does not cross zero within the horizon. The demo deficit is a Phase-4 what-if.
        UUID freelancerId = seededClientId("client_003_freelancer");

        mockMvc.perform(get("/api/v1/clients/{id}/forecast", freelancerId).param("horizonDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points.length()").value(31))
                .andExpect(jsonPath("$.data.deficitDate").doesNotExist());
    }

    @Test
    void multiMonthHorizonsReturnValidSeries() throws Exception {
        UUID clientId = seededClientId("client_001_family");

        for (int horizon : new int[] {90, 180, 365}) {
            mockMvc.perform(get("/api/v1/clients/{id}/forecast", clientId)
                            .param("horizonDays", String.valueOf(horizon)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OK"))
                    .andExpect(jsonPath("$.data.points.length()").value(horizon + 1));
        }
    }

    @Test
    void largeVolumePersonaRespondsWithinBudget() throws Exception {
        UUID techId = seededClientId("client_002_tech_bro");

        long startNanos = System.nanoTime();
        mockMvc.perform(get("/api/v1/clients/{id}/forecast", techId).param("horizonDays", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.points.length()").value(366));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertThat(elapsed)
                .as("forecast under the 2.5 s NFR for the large-volume persona (DESIGN.md §9)")
                .isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void unknownIdReturns404ErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/forecast", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void outOfRangeHorizonReturns400ValidationEnvelope() throws Exception {
        UUID clientId = seededClientId("client_001_family");

        mockMvc.perform(get("/api/v1/clients/{id}/forecast", clientId).param("horizonDays", "0"))
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
