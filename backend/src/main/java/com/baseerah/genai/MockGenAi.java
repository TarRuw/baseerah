package com.baseerah.genai;

import com.baseerah.stress.Zone;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, telemetry-grounded default {@link GenAiClient} (FR-03, DESIGN.md §9). Requires no API key
 * and no network, so the live demo runs offline; identical input always yields identical output (no
 * randomness, no clock), so demos and tests are reproducible. Wired as the {@code mock} bean by
 * {@link GenAiConfig}; it holds no state and no dependencies.
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
 * <p>Replies are English (the interface carries no locale); Arabic <em>keyword matching</em> is supported as
 * the step requires, while Arabic reply text is deferred to the remote adapter / a later step.
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

    @Override
    public ChatReply chat(ChatContext context, String message) {
        return new ChatReply(isScenario(message) ? scenarioReply(context) : genericReply(context));
    }

    @Override
    public InvoiceParseResult parseInvoice(byte[] image) {
        // Mock mode performs no OCR (DESIGN §9): return a deterministic, clearly-labelled stub so the Step 3.5
        // invoice-upload flow has a stable, offline shape to render. Independent of the image bytes on purpose.
        return new InvoiceParseResult(
                "Sample Merchant",
                new BigDecimal("349.00"),
                "Shopping",
                "Log this 349 SAR expense and check its impact on your 30-day forecast. "
                        + "(Mock mode — connect a GenAI provider for real invoice OCR.)");
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
    private static String scenarioReply(ChatContext ctx) {
        String scoreLine = "Your current health score is " + ctx.currentScore()
                + " (" + zoneLabel(ctx.zone()) + ").";
        BigDecimal surplus = ctx.surplus();
        if (surplus.signum() <= 0) {
            return "A 2,100 SAR/mo lease is not sustainable right now — your recurring outflows already meet "
                    + "or exceed your income, so there is no monthly surplus to absorb it.\n\n"
                    + "• " + scoreLine + "\n"
                    + "• Any new fixed payment would pull your first liquidity deficit earlier each cycle.\n\n"
                    + "Recommendation: free up surplus first, or use the Loan Affordability tab to test a "
                    + "smaller amount.";
        }
        long pct = Math.round(LEASE_PAYMENT * 100.0 / surplus.doubleValue());
        BigDecimal safe = BigDecimal.valueOf(Math.round(COMFORTABLE_FRACTION * surplus.doubleValue()));
        return "A 2,100 SAR/mo lease would take about " + pct + "% of your ~" + money(surplus)
                + " SAR monthly surplus.\n\n"
                + "• " + scoreLine + "\n"
                + "• 3-month view: the payment repeats every cycle and steadily erodes your buffer.\n"
                + "• 12-month view: sustained strain at this level pulls your health score toward the next band "
                + "down.\n\n"
                + "Recommendation: keep the payment under " + money(safe) + " SAR/mo, or open the Loan "
                + "Affordability tab for the exact installment and score impact.";
    }

    /** The default 24-month cash-flow analysis, referencing the client's telemetry. */
    private static String genericReply(ChatContext ctx) {
        return "I analyzed your last 24 months of cash flow. Your health score is " + ctx.currentScore()
                + " (" + zoneLabel(ctx.zone()) + "), with roughly " + money(ctx.monthlyIncome())
                + " SAR coming in and " + money(ctx.monthlyOutflow()) + " SAR going out each month (~"
                + money(ctx.surplus()) + " SAR surplus). Ask about a specific decision, or open the Loan "
                + "Affordability tab to see its exact impact on your forecast.";
    }

    /** Friendly label for a zone (e.g. {@code OPTIMAL} → "Optimal"). */
    private static String zoneLabel(Zone zone) {
        String name = zone.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Whole-SAR, comma-grouped money for display (matches the prototype's {@code fmt}, e.g. 4300 → "4,300"). */
    private static String money(BigDecimal value) {
        return String.format(Locale.US, "%,d", Math.round(value.doubleValue()));
    }
}
