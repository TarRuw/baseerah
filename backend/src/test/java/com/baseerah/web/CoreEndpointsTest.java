package com.baseerah.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseerah.client.Client;
import com.baseerah.client.ClientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-layer coverage for the Step 1.4 read endpoints, driven against the live Liquibase-managed
 * PostgreSQL seeded by {@code MockDataSeeder} (the project run model: {@code docker-compose up}). Skipped
 * — not failed — when Postgres is not reachable on {@code localhost:5432}, matching
 * {@code MockDataSeederTest}/{@code PersistenceContextTest}.
 *
 * <p>Verifies: the envelope shape for each endpoint, that the raw account external id never leaks (only
 * {@code tokenizedAccountId}), newest-first + size-capped pagination, and the shared 404 error envelope
 * for unknown and malformed ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("com.baseerah.web.CoreEndpointsTest#postgresReachable")
class CoreEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Client anySeededClient() {
        return clientRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AssertionError("no seeded clients present — run the seeder first"));
    }

    @Test
    void listClientsReturnsSeededPersonasInEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].externalId").exists())
                .andExpect(jsonPath("$.data[0].persona").exists());
    }

    @Test
    void accountsAreEnvelopedAndHideRawExternalId() throws Exception {
        UUID clientId = anySeededClient().getId();

        mockMvc.perform(get("/api/v1/clients/{id}/accounts", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].tokenizedAccountId").exists())
                // The raw account external id must never be serialized (SAMA §9).
                .andExpect(jsonPath("$.data[0].externalId").doesNotExist());
    }

    @Test
    void transactionsAreNewestFirstAndRespectSize() throws Exception {
        UUID clientId = anySeededClient().getId();

        MvcResult result = mockMvc.perform(
                        get("/api/v1/clients/{id}/transactions", clientId).param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("content");
        // Newest-first: each booking date is <= the previous one.
        Instant previous = null;
        for (JsonNode tx : content) {
            Instant current = Instant.parse(tx.path("bookingDate").asText());
            if (previous != null) {
                assertThat(current).isBeforeOrEqualTo(previous);
            }
            previous = current;
        }
    }

    @Test
    void unknownIdReturns404ErrorEnvelopeOnEveryEndpoint() throws Exception {
        String unknown = UUID.randomUUID().toString();
        for (String path : new String[] {
                "/api/v1/clients/" + unknown + "/accounts",
                "/api/v1/clients/" + unknown + "/transactions"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("ERROR"))
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Test
    void malformedIdReturns404ErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/clients/{id}/accounts", "not-a-uuid"))
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
