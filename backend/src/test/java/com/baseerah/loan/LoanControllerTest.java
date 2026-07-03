package com.baseerah.loan;

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

/**
 * Web-layer coverage for the Step 3.3 loan-simulate endpoint, driven against the live Liquibase-managed
 * PostgreSQL seeded by {@code MockDataSeeder}. Skipped — not failed — when Postgres is unreachable on
 * {@code localhost:5432}, matching {@code ForecastControllerTest}/{@code CoreEndpointsTest}. This is the
 * end-to-end check that income/essentials are derived from a real seeded persona's telemetry (not the
 * prototype constants); the pure §5.3 arithmetic is pinned separately by {@link LoanCalculatorTest}.
 *
 * <p>The instalment/total are telemetry-independent (they depend only on principal/rate/term), so they are
 * asserted exactly; DTI, verdict, and projected score depend on the derived telemetry, so they are asserted
 * as well-formed and in-band. Also verifies the shared 404 (unknown id) and 400 (invalid body) envelopes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.baseerah.loan.LoanControllerTest#postgresReachable")
class LoanControllerTest {

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
    void seededClientReturnsLoanSimulateEnvelope() throws Exception {
        UUID clientId = seededClientId("client_001_family");

        mockMvc.perform(post("/api/v1/clients/{id}/loan-simulate", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal\":36000,\"rate\":0,\"term\":36}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                // installment/total depend only on principal/rate/term: 36000/36 = 1000, ×36 = 36000.
                .andExpect(jsonPath("$.data.installment").value(1000))
                .andExpect(jsonPath("$.data.total").value(36000))
                // DTI + verdict + score depend on telemetry derived from the seeded persona.
                .andExpect(jsonPath("$.data.dti").isNumber())
                .andExpect(jsonPath("$.data.verdict").value(Matchers.in(new String[] {
                        "Comfortably affordable", "Strains liquidity", "Not affordable"})))
                .andExpect(jsonPath("$.data.verdictColor").value(Matchers.in(new String[] {
                        "#1D9E63", "#E5A63A", "#E0574F"})))
                .andExpect(jsonPath("$.data.dtiColor").value(Matchers.in(new String[] {
                        "#1D9E63", "#E5A63A", "#E0574F"})))
                // projected score is clamped to [9, 84] (DESIGN §5.3).
                .andExpect(jsonPath("$.data.projectedScore")
                        .value(Matchers.allOf(Matchers.greaterThanOrEqualTo(9), Matchers.lessThanOrEqualTo(84))));
    }

    @Test
    void unknownIdReturns404ErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/clients/{id}/loan-simulate", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal\":10000,\"rate\":5,\"term\":24}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void invalidBodyReturns400ValidationEnvelope() throws Exception {
        UUID clientId = seededClientId("client_001_family");

        // Negative principal and zero term both violate the request constraints.
        mockMvc.perform(post("/api/v1/clients/{id}/loan-simulate", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal\":-5000,\"rate\":5,\"term\":0}"))
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
