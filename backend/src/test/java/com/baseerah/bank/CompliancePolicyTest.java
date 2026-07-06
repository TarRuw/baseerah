package com.baseerah.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseerah.account.Account;
import com.baseerah.account.AccountRepository;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
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
 * Step 7.1 compliance coverage (DESIGN §9): the bank-side payloads must be provably leak-free (only
 * {@code tokenized_account_id} ever crosses the boundary), the SAMA tokenization / NDMO residency policy
 * toggles must drive observable behaviour, and the persisted risk policy must gate underwriting verdicts.
 * Driven against the live Liquibase-managed Postgres seeded by {@code MockDataSeeder} +
 * {@code BankApplicantSeeder}; skipped — not failed — when Postgres is unreachable, matching the other
 * DB-gated suites.
 *
 * <p>{@code @Transactional}: MockMvc dispatches in-thread, so each {@code PUT /risk-policy} write is visible
 * to the report/portfolio calls that follow within the same test, and the whole thing rolls back — the
 * seeded queue and the singleton policy are never mutated across runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.baseerah.bank.CompliancePolicyTest#postgresReachable")
class CompliancePolicyTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private AccountRepository accountRepository;

    /** The VIP applicant is linked to a seeded consumer, so its report carries real (tokenized) accounts. */
    private static final String VIP_SEED_KEY = "APP-VIP-EXPANSION";

    private UUID applicationId(String seedKey) {
        return loanApplicationRepository.findBySeedKey(seedKey)
                .orElseThrow(() -> new AssertionError("applicant not seeded: " + seedKey))
                .getId();
    }

    private List<Account> vipAccounts() {
        UUID clientRef = loanApplicationRepository.findBySeedKey(VIP_SEED_KEY)
                .orElseThrow(() -> new AssertionError("applicant not seeded: " + VIP_SEED_KEY))
                .getClientRef();
        List<Account> accounts = accountRepository.findByClientId(clientRef);
        assertThat(accounts).as("VIP client must have seeded accounts to reason about").isNotEmpty();
        return accounts;
    }

    // ── No-leak guarantee ────────────────────────────────────────────────────────────────────────────

    @Test
    void bankReportExposesTokensAndNeverRawAccountIds() throws Exception {
        List<Account> accounts = vipAccounts();

        String body = mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", applicationId(VIP_SEED_KEY)))
                .andExpect(status().isOk())
                // The report carries the tokenized references (default policy has tokenization on)…
                .andExpect(jsonPath("$.data.tokenizedAccounts", Matchers.not(Matchers.empty())))
                .andReturn().getResponse().getContentAsString();

        assertNoRawAccountIdLeaks(body, accounts);
        // …and every one of them is present as its token.
        for (Account account : accounts) {
            assertThat(body).contains(account.getTokenizedAccountId());
        }
    }

    @Test
    void portfolioExposesTokensAndNeverRawAccountIds() throws Exception {
        List<Account> accounts = vipAccounts();

        String body = mockMvc.perform(get("/api/v1/bank/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitoring[0].tokenizedAccounts").exists())
                .andReturn().getResponse().getContentAsString();

        assertNoRawAccountIdLeaks(body, accounts);
        // The VIP is in the active book, so at least its tokens surface in the monitoring rows.
        for (Account account : accounts) {
            assertThat(body).contains(account.getTokenizedAccountId());
        }
    }

    /** Scan a serialized bank payload for the DB's raw account identifiers — none may appear. */
    private static void assertNoRawAccountIdLeaks(String body, List<Account> accounts) {
        for (Account account : accounts) {
            assertThat(body)
                    .as("raw account UUID must never leak into a bank payload")
                    .doesNotContain(account.getId().toString());
            if (account.getExternalId() != null) {
                assertThat(body)
                        .as("raw account external id (acc-…) must never leak into a bank payload")
                        .doesNotContain(account.getExternalId());
            }
        }
    }

    // ── Toggle-driven behaviour ──────────────────────────────────────────────────────────────────────

    @Test
    void tokenizationOffHidesTheTokenSurfaceButStillLeaksNothing() throws Exception {
        List<Account> accounts = vipAccounts();
        putPolicy(50, 71, true, false); // tokenization OFF, residency untouched

        String body = mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", applicationId(VIP_SEED_KEY)))
                .andExpect(status().isOk())
                // With the SAMA control off, no account reference is surfaced at all…
                .andExpect(jsonPath("$.data.tokenizedAccounts", Matchers.empty()))
                .andReturn().getResponse().getContentAsString();

        // …and we still never fall back to raw ids — the toggle changes the token surface, not the guarantee.
        assertNoRawAccountIdLeaks(body, accounts);
        for (Account account : accounts) {
            assertThat(body).doesNotContain(account.getTokenizedAccountId());
        }
    }

    @Test
    void ndmoResidencyTogglesTheExportGateAndResidencyMarker() throws Exception {
        UUID vipId = applicationId(VIP_SEED_KEY);

        // Default policy: residency ON → local marker, export gated closed.
        mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", vipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataResidency").value("KSA"))
                .andExpect(jsonPath("$.data.exportAllowed").value(false));

        // Flip residency OFF → the very next request observes the change.
        putPolicy(50, 71, false, true);
        mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", vipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataResidency").value("UNRESTRICTED"))
                .andExpect(jsonPath("$.data.exportAllowed").value(true));
        mockMvc.perform(get("/api/v1/bank/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataResidency").value("UNRESTRICTED"))
                .andExpect(jsonPath("$.data.exportAllowed").value(true));
    }

    // ── Policy-driven auto-decline ───────────────────────────────────────────────────────────────────

    @Test
    void raisingStaminaFloorAutoDeclinesAnOtherwiseApprovableApplicant() throws Exception {
        // Pick a linked applicant that underwrites to a *passing* verdict with sub-maximal stamina under the
        // default policy, so raising the floor above its stamina is provably what turns it into a decline
        // (rather than a pre-existing §5.5 BAD). A perfect-stamina (100) applicant like the VIP correctly
        // passes any valid floor, so it can't demonstrate this path.
        for (String seedKey : List.of("APP-TECH-CONSOLIDATE", "APP-FREELANCER-CASHFLOW", "APP-FAMILY-AUTO")) {
            UUID id = applicationId(seedKey);
            String body = mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", id))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String verdict = JsonPath.read(body, "$.data.verdict");
            int stamina = ((Number) JsonPath.read(body, "$.data.staminaScore")).intValue();
            if (verdict.equals("BAD") || stamina >= 100) {
                continue;
            }

            // Raise the floor just above this applicant's stamina → the next report auto-declines.
            putPolicy(stamina + 1, 71, true, true);
            mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.verdict").value("BAD"))
                    .andExpect(jsonPath("$.data.riskTier").value("C"));
            return;
        }
        throw new AssertionError("no linked applicant with a passing verdict and sub-100 stamina to screen");
    }

    @Test
    void loweringDtiThresholdAutoDeclinesAnOtherwiseApprovableApplicant() throws Exception {
        UUID vipId = applicationId(VIP_SEED_KEY);

        // Drop the DTI auto-decline ceiling to zero → any positive DTI meets it → auto-decline.
        putPolicy(50, 0, true, true);
        mockMvc.perform(post("/api/v1/bank/applicants/{id}/report", vipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value("BAD"))
                .andExpect(jsonPath("$.data.riskTier").value("C"));
    }

    /** PUT the singleton risk policy with the given guardrails and compliance toggles. */
    private void putPolicy(int staminaFloor, int autoDeclineThreshold, boolean ndmoResidency,
            boolean tokenization) throws Exception {
        String body = "{"
                + "\"staminaFloor\":" + staminaFloor + ","
                + "\"autoDeclineThreshold\":" + autoDeclineThreshold + ","
                + "\"ndmoResidency\":" + ndmoResidency + ","
                + "\"tokenization\":" + tokenization + ","
                + "\"samaLastSync\":null}";
        mockMvc.perform(put("/api/v1/bank/risk-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
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
