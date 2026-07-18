package com.baseerah.api.genai;

import java.math.BigDecimal;

/**
 * API view of a parsed-invoice action (FR-03, DESIGN.md §6). Serialized inside the shared success envelope by
 * {@code POST /api/v1/clients/{id}/chat/invoice}. The gateway's {@code GenAiClient.InvoiceParseResult} never
 * crosses the controller boundary — this immutable web projection does; {@link ChatWebMapper} maps between
 * them (Global Rules). In mock mode the fields are a deterministic stub (no OCR). The shape is byte-identical
 * to the pre-refactor response (the gateway record was previously serialized directly), so no behaviour
 * changes.
 *
 * @param merchant        the detected merchant (stub in mock mode)
 * @param amount          the detected amount in SAR (stub in mock mode)
 * @param category        the suggested spending category
 * @param suggestedAction a human-readable next step for the user
 */
public record InvoiceResponse(
        String merchant, BigDecimal amount, String category, String suggestedAction) {
}
