package com.baseerah.application.infrastructure.gateway.genai;

import com.baseerah.shared.Messages;
import com.baseerah.domain.stress.Zone;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, telemetry-grounded default {@link GenAiClient} (FR-03, DESIGN.md §9). Requires no API key
 * and no network, so the live demo runs offline; identical input always yields identical output (no
 * randomness, no clock), so demos and tests are reproducible. Wired as the {@code mock} bean by
 * {@link com.baseerah.config.GenAiConfig}; it holds no state beyond the shared {@link Messages} resolver.
 *
 * <p>{@link #chat} keyword-routes to one of two replies, both grounded in the caller-supplied
 * {@link GenAiClient.ChatContext} (the client's own score, zone and monthly cash flow — never the
 * prototype's hardcoded demo constants, per the Global Rule):
 * <ul>
 *   <li><strong>Scenario</strong> — a car / lease / vehicle / {@code 2100} question (and its Arabic
 *       equivalents) frames the affordability and score impact of the 2,100 SAR/mo lease against the
 *       client's surplus.</li>
 *   <li><strong>Generic</strong> — anything else gets a 24-month cash-flow analysis referencing the client's
 *       telemetry.</li>
 * </ul>
 *
 * <p><strong>Locale (Step 8.1, I18N-01).</strong> The reply text is resolved from the {@code chat.*} message
 * bundle for the request locale, so {@code Accept-Language: ar} yields an Arabic reply and {@code en} an
 * English one — the grounding figures (score, income, outflow, surplus, %) are identical, injected
 * pre-formatted with Western digits. Arabic <em>keyword matching</em> was already supported; now the reply
 * itself follows the locale too.
 */
public class MockGenAi implements GenAiClient {

    /** The scenario's fixed monthly lease payment (SAR), matching the prototype's "2,100 SAR car lease". */
    static final int LEASE_PAYMENT = 2100;

    /** Comfortable-payment fraction of surplus, mirrored from {@code LoanCalculator} (DESIGN §5.3). */
    private static final double COMFORTABLE_FRACTION = 0.50;

    /** Lower-cased Latin tokens that route to the scenario reply. */
    private static final List<String> LATIN_SCENARIO_KEYWORDS =
            List.of("car", "lease", "vehicle", "2100", "2,100");

    /** Arabic tokens that route to the scenario reply (سيارة=car, تأجير/إيجار=lease, مركبة=vehicle, ٢١٠٠=2100). */
    private static final List<String> ARABIC_SCENARIO_KEYWORDS =
            List.of("سيارة", "تأجير", "مركبة", "إيجار", "٢١٠٠", "٢٬١٠٠");

    private final Messages messages;

    public MockGenAi(Messages messages) {
        this.messages = messages;
    }

    @Override
    public ChatReply chat(ChatContext context, String message) {
        return new ChatReply(isScenario(message) ? scenarioReply(context) : genericReply(context));
    }

    @Override
    public InvoiceParseResult parseInvoice(byte[] image) {
        // Mock mode performs no OCR (DESIGN §9): return a deterministic, clearly-labelled stub so the Step 3.5
        // invoice-upload flow has a stable, offline shape to render. Independent of the image bytes on purpose;
        // the suggested action is localised for the request (Step 8.1).
        return new InvoiceParseResult(
                "Sample Merchant",
                new BigDecimal("349.00"),
                "Shopping",
                messages.get("chat.invoice.action", "349"));
    }

    /** True when the message mentions the car/lease scenario in English or Arabic. */
    private static boolean isScenario(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String keyword : LATIN_SCENARIO_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        for (String keyword : ARABIC_SCENARIO_KEYWORDS) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** The 2,100 SAR lease scenario, framed against the client's real surplus and current score. */
    private String scenarioReply(ChatContext ctx) {
        String score = Integer.toString(ctx.currentScore());
        String zone = zoneLabel(ctx.zone());
        BigDecimal surplus = ctx.surplus();
        if (surplus.signum() <= 0) {
            return messages.get("chat.scenario.unsustainable", score, zone);
        }
        String pct = Long.toString(Math.round(LEASE_PAYMENT * 100.0 / surplus.doubleValue()));
        String safe = money(BigDecimal.valueOf(Math.round(COMFORTABLE_FRACTION * surplus.doubleValue())));
        return messages.get("chat.scenario.affordable", pct, money(surplus), score, zone, safe);
    }

    /** The default 24-month cash-flow analysis, referencing the client's telemetry. */
    private String genericReply(ChatContext ctx) {
        return messages.get("chat.generic", Integer.toString(ctx.currentScore()), zoneLabel(ctx.zone()),
                money(ctx.monthlyIncome()), money(ctx.monthlyOutflow()), money(ctx.surplus()));
    }

    /** Localised label for a zone (e.g. {@code OPTIMAL} → "Optimal" / "مثالي") for the request locale. */
    private String zoneLabel(Zone zone) {
        return messages.get("zone." + zone.name());
    }

    /** Whole-SAR, comma-grouped money for display (matches the prototype's {@code fmt}, e.g. 4300 → "4,300"). */
    private static String money(BigDecimal value) {
        return String.format(Locale.US, "%,d", Math.round(value.doubleValue()));
    }
}
