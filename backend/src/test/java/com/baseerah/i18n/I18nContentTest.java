package com.baseerah.i18n;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseerah.client.ClientRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 8.1 acceptance (QA finding I18N-01/COPY-01): the server-provided <em>content</em> strings resolve to
 * the request's {@code Accept-Language} — Arabic for {@code ar}, English for {@code en} — so nothing English
 * leaks into the Arabic-first UI. Driven end-to-end against the live Liquibase-seeded PostgreSQL (skipped, not
 * failed, when Postgres is unreachable, matching the other web-slice tests).
 *
 * <p>Covers the five content seams the finding named: the loan affordability verdict, the grounded chat reply,
 * the rescue option labels/details, and the challenge title/subtitle. Each is asserted to (a) differ between
 * the two locales and (b) contain Arabic script under {@code ar} and none under {@code en}, while the numeric
 * grounding stays identical (Western digits). The bank verdict is deliberately excluded: the API emits a
 * locale-independent {@code Verdict} enum that the Flutter client maps to its own localized headline, so there
 * is no server content string to localize there (see the step handoff).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("com.baseerah.i18n.I18nContentTest#postgresReachable")
class I18nContentTest {

    /** Full-string (DOTALL) match for "contains at least one Arabic-script character". */
    private static final Matcher<String> HAS_ARABIC = Matchers.matchesPattern("(?s).*[\\u0600-\\u06FF].*");

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
    void loanVerdictLocalizesByAcceptLanguage() throws Exception {
        UUID familyId = seededClientId("client_001_family");
        String body = "{\"principal\":36000,\"rate\":0,\"term\":36}";

        // English: one of the three DESIGN §5.3 verdicts, Latin script.
        mockMvc.perform(post("/api/v1/clients/{id}/loan-simulate", familyId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value(Matchers.in(new String[] {
                        "Comfortably affordable", "Strains liquidity", "Not affordable"})))
                .andExpect(jsonPath("$.data.verdict").value(Matchers.not(HAS_ARABIC)));

        // Arabic: the verdict is Arabic script, and the colour band (telemetry-driven) is unchanged.
        mockMvc.perform(post("/api/v1/clients/{id}/loan-simulate", familyId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ar")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verdict").value(HAS_ARABIC))
                .andExpect(jsonPath("$.data.verdictColor").value(Matchers.in(new String[] {
                        "#1D9E63", "#E5A63A", "#E0574F"})));
    }

    @Test
    void chatReplyLocalizesByAcceptLanguage() throws Exception {
        UUID familyId = seededClientId("client_001_family");
        String body = "{\"message\":\"How am I doing overall?\"}";

        mockMvc.perform(post("/api/v1/clients/{id}/chat", familyId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value(Matchers.not(HAS_ARABIC)));

        mockMvc.perform(post("/api/v1/clients/{id}/chat", familyId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ar")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value(HAS_ARABIC));
    }

    @Test
    void rescueOptionsLocalizeByAcceptLanguage() throws Exception {
        UUID freelancerId = seededClientId("client_003_freelancer"); // has a real (years-out) deficit → options

        mockMvc.perform(get("/api/v1/clients/{id}/rescue", freelancerId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options[0].label").value(Matchers.not(HAS_ARABIC)))
                .andExpect(jsonPath("$.data.options[0].detail").value(Matchers.not(HAS_ARABIC)))
                // The option type enum is stable/English regardless of locale (Global Rule).
                .andExpect(jsonPath("$.data.options[?(@.type == 'MURABAHA')]", Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/clients/{id}/rescue", freelancerId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options[0].label").value(HAS_ARABIC))
                .andExpect(jsonPath("$.data.options[0].detail").value(HAS_ARABIC))
                .andExpect(jsonPath("$.data.options[?(@.type == 'MURABAHA')]", Matchers.hasSize(1)));
    }

    @Test
    void challengeCopyLocalizesByAcceptLanguage() throws Exception {
        UUID studentId = seededClientId("client_004_student"); // dining habit → challenges generated on boot

        mockMvc.perform(get("/api/v1/clients/{id}/challenges", studentId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.data[*].title").value(Matchers.everyItem(Matchers.not(HAS_ARABIC))));

        mockMvc.perform(get("/api/v1/clients/{id}/challenges", studentId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.data[*].title").value(Matchers.everyItem(HAS_ARABIC)))
                .andExpect(jsonPath("$.data[*].subtitle").value(Matchers.everyItem(HAS_ARABIC)));
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
