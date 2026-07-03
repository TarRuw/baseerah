package com.baseerah.genai;

import com.baseerah.stress.Zone;
import java.math.BigDecimal;

/**
 * Pluggable conversational-AI seam (FR-03, DESIGN.md §2, §3, §5, §6). Controllers depend only on this
 * interface — never a concrete implementation (Global Rule) — so the provider can be swapped by
 * configuration: {@code GENAI_PROVIDER=mock} (default) resolves the deterministic, key-free
 * {@link MockGenAi}; {@code GENAI_PROVIDER=remote} is reserved for the Phase-7 {@code RemoteGenAi} adapter
 * (Step 7.2), wired through the same switch in {@link GenAiConfig}.
 *
 * <p>The mock guarantees the demo works offline (DESIGN §9 reliability); the real adapter can stream with a
 * {@code < 1.0 s} time-to-first-token later. All grounding data reaches the client through {@link ChatContext}
 * so the interface stays free of Spring, JPA and I/O concerns.
 */
public interface GenAiClient {

    /**
     * Answer a free-text question, grounded in the client's telemetry.
     *
     * @param context the client's current telemetry summary (score, zone, monthly cash flow)
     * @param message the user's message
     * @return the assistant's reply
     */
    ChatReply chat(ChatContext context, String message);

    /**
     * Parse an uploaded invoice image into a suggested action.
     *
     * @param image the raw uploaded image bytes
     * @return the parsed merchant/amount/category and a suggested action
     */
    InvoiceParseResult parseInvoice(byte[] image);

    /**
     * A snapshot of the telemetry a reply is grounded in — deliberately a small, provider-agnostic value
     * object (no entities, no Spring). Built by {@link ChatService} from the client's latest stress score and
     * a trailing transaction window before the {@link GenAiClient} is called.
     *
     * @param profileLabel   the client's persona label (e.g. "Family / dual income")
     * @param currentScore   the client's current Financial Stress Score (0–100, higher = healthier)
     * @param zone           the health band for {@code currentScore}
     * @param monthlyIncome  mean monthly inflow over the trailing window (SAR)
     * @param monthlyOutflow mean monthly outflow over the trailing window (SAR)
     */
    record ChatContext(
            String profileLabel,
            int currentScore,
            Zone zone,
            BigDecimal monthlyIncome,
            BigDecimal monthlyOutflow) {

        /** Mean monthly surplus (income − outflow); may be zero or negative for a strained client. */
        public BigDecimal surplus() {
            return monthlyIncome.subtract(monthlyOutflow);
        }
    }

    /** The assistant's reply. Serialized directly inside the {@code {status,data}} envelope as {@code {reply}}. */
    record ChatReply(String reply) {
    }

    /**
     * A parsed-invoice action. In mock mode this is a deterministic stub (no OCR) so the Step 3.5
     * invoice-upload flow has a stable shape to render offline; the remote adapter fills it from real OCR.
     *
     * @param merchant        the detected merchant (stub in mock mode)
     * @param amount          the detected amount in SAR (stub in mock mode)
     * @param category        the suggested spending category
     * @param suggestedAction a human-readable next step for the user
     */
    record InvoiceParseResult(
            String merchant,
            BigDecimal amount,
            String category,
            String suggestedAction) {
    }
}
