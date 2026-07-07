package com.baseerah.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.common.Messages;
import com.baseerah.genai.GenAiClient.ChatContext;
import com.baseerah.genai.GenAiClient.ChatReply;
import com.baseerah.genai.GenAiClient.InvoiceParseResult;
import com.baseerah.stress.Zone;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * Unit tests for {@link MockGenAi} (DESIGN §9) — no Spring, no database. Verifies keyword routing (car /
 * lease / vehicle / {@code 2100} and an Arabic equivalent → scenario; anything else → 24-month analysis),
 * that replies are grounded in the supplied {@link ChatContext}, and that identical input yields identical
 * output. Provider selection and keyless fallback live in {@code GenAiProviderSelectionTest}.
 */
class MockGenAiTest {

    // Backed by the real messages*.properties bundles so the English assertions below pin the actual copy and
    // the Arabic test exercises the ar bundle (Step 8.1). The request locale drives resolution via
    // LocaleContextHolder — the default is English, so the existing assertions are unaffected.
    private final MockGenAi mock = new MockGenAi(Messages.forTests());

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    // Family-persona figures used purely as a known ChatContext to pin the grounding math:
    // income 18,500 − outflow 14,200 = surplus 4,300; 2,100 / 4,300 ≈ 49%; comfortable = 0.5 × 4,300 = 2,150.
    private static final ChatContext FAMILY = new ChatContext(
            "Family / dual income", 62, Zone.WARNING,
            new BigDecimal("18500"), new BigDecimal("14200"));

    @Test
    void carKeywordRoutesToScenarioReplyGroundedInClientNumbers() {
        ChatReply reply = mock.chat(FAMILY, "Can I afford a car lease?");

        assertThat(reply.reply())
                .contains("2,100 SAR/mo lease")   // the scenario framing
                .contains("49%")                  // 2,100 / 4,300 surplus, grounded (not the prototype's 89%)
                .contains("~4,300 SAR")           // the client's real surplus
                .contains("score is 62")          // the client's real current score
                .contains("2,150 SAR/mo");        // comfortable cap = 0.5 × surplus
    }

    @Test
    void numericAndArabicKeywordsAlsoRouteToScenario() {
        assertThat(mock.chat(FAMILY, "What about a 2100 payment?").reply()).contains("2,100 SAR/mo lease");
        assertThat(mock.chat(FAMILY, "هل أستطيع تحمّل إيجار سيارة؟").reply()).contains("2,100 SAR/mo lease");
        assertThat(mock.chat(FAMILY, "vehicle finance").reply()).contains("2,100 SAR/mo lease");
    }

    @Test
    void nonScenarioMessageRoutesToTwentyFourMonthAnalysis() {
        ChatReply reply = mock.chat(FAMILY, "How am I doing overall?");

        assertThat(reply.reply())
                .contains("last 24 months")
                .contains("score is 62")
                .doesNotContain("2,100 SAR/mo lease");
    }

    @Test
    void scenarioReplyIsSustainabilityWarningWhenSurplusIsNonPositive() {
        ChatContext strained = new ChatContext(
                "Strained", 28, Zone.CRITICAL, new BigDecimal("5000"), new BigDecimal("6000"));

        assertThat(mock.chat(strained, "car lease?").reply())
                .contains("not sustainable")
                .contains("score is 28");
    }

    @Test
    void identicalInputYieldsIdenticalOutput() {
        assertThat(mock.chat(FAMILY, "car lease?").reply())
                .isEqualTo(mock.chat(FAMILY, "car lease?").reply());
        assertThat(mock.chat(FAMILY, "how are my finances?").reply())
                .isEqualTo(mock.chat(FAMILY, "how are my finances?").reply());
    }

    @Test
    void replyFollowsRequestLocaleWithIdenticalFigures() {
        // English baseline for the scenario reply.
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String en = mock.chat(FAMILY, "car lease?").reply();

        // Arabic (Accept-Language: ar) — the copy must be Arabic, yet the grounding figures are identical
        // (Western digits): same 49% of a 4,300 surplus, same score 62 (I18N-01).
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ar"));
        String ar = mock.chat(FAMILY, "car lease?").reply();

        assertThat(ar)
                .isNotEqualTo(en)
                .contains("2,100")   // the lease figure, Western digits
                .contains("49")      // same % of surplus
                .contains("4,300")   // same surplus
                .contains("62")      // same current score
                .containsPattern("[\\u0600-\\u06FF]"); // contains Arabic script
        assertThat(en).doesNotContainPattern("[\\u0600-\\u06FF]"); // English stays Latin
    }

    @Test
    void parseInvoiceReturnsDeterministicStub() {
        InvoiceParseResult first = mock.parseInvoice(new byte[] {1, 2, 3});
        InvoiceParseResult second = mock.parseInvoice(new byte[] {9, 9});

        assertThat(first).isEqualTo(second); // independent of image bytes (no OCR in mock mode)
        assertThat(first.merchant()).isNotBlank();
        assertThat(first.amount()).isNotNull();
        assertThat(first.suggestedAction()).isNotBlank();
    }
}
