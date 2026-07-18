package com.baseerah.api.genai;

/**
 * API view of the assistant's reply (FR-03, DESIGN.md §6). Serialized inside the shared success envelope by
 * {@code POST /api/v1/clients/{id}/chat} as {@code {reply}}. The gateway's provider-agnostic
 * {@code GenAiClient.ChatReply} never crosses the controller boundary — this immutable web projection does;
 * {@link ChatWebMapper} maps between them (Global Rules). The shape is byte-identical to the pre-refactor
 * response (the gateway record was previously serialized directly), so no behaviour changes.
 *
 * @param reply the assistant's reply text
 */
public record ChatReplyResponse(String reply) {
}
