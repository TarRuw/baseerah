package com.baseerah.api.genai;

import com.baseerah.application.infrastructure.gateway.genai.GenAiClient.ChatReply;
import com.baseerah.application.infrastructure.gateway.genai.GenAiClient.InvoiceParseResult;

/**
 * Maps the gateway's provider-agnostic value records ({@link ChatReply}, {@link InvoiceParseResult}) to their
 * API views ({@link ChatReplyResponse}, {@link InvoiceResponse}). Pure and static — the controller calls it
 * directly; no framework or JPA types cross this mapper. The reply copy is already localised by the
 * {@code MockGenAi} / {@code RemoteGenAi} gateway for the request locale (Step 8.1, I18N-01), so this mapper is
 * a pure field copy and the served JSON is byte-identical to the pre-refactor responses (the gateway records
 * were previously serialized directly).
 */
public final class ChatWebMapper {

    private ChatWebMapper() {
    }

    /** Map a gateway {@link ChatReply} to its API view {@link ChatReplyResponse}. */
    public static ChatReplyResponse toChatResponse(ChatReply reply) {
        return new ChatReplyResponse(reply.reply());
    }

    /** Map a gateway {@link InvoiceParseResult} to its API view {@link InvoiceResponse}. */
    public static InvoiceResponse toInvoiceResponse(InvoiceParseResult result) {
        return new InvoiceResponse(
                result.merchant(), result.amount(), result.category(), result.suggestedAction());
    }
}
