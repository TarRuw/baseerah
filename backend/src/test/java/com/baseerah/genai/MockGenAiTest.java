package com.baseerah.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.genai.GenAiClient.ChatContext;
import com.baseerah.genai.GenAiClient.ChatReply;
import com.baseerah.genai.GenAiClient.InvoiceParseResult;
import com.baseerah.stress.Zone;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MockGenAi} (DESIGN §9) — no Spring, no database. Verifies keyword routing (car /
 * lease / vehicle / {@code 2100} and an Arabic equivalent → scenario; anything else → 24-month analysis),
 * that replies are grounded in the supplied {@link ChatContext}, and that identical input yields identical
 * output. Provider selection and keyless fallback live in {@code GenAiProviderSelectionTest}.
 */
class MockGenAiTest {

    private final MockGenAi mock = new MockGenAi();

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
    void parseInvoiceReturnsDeterministicStub() {
        InvoiceParseResult first = mock.parseInvoice(new byte[] {1, 2, 3});
        InvoiceParseResult second = mock.parseInvoice(new byte[] {9, 9});

        assertThat(first).isEqualTo(second); // independent of image bytes (no OCR in mock mode)
        assertThat(first.merchant()).isNotBlank();
        assertThat(first.amount()).isNotNull();
        assertThat(first.suggestedAction()).isNotBlank();
    }
}
