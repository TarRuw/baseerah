package com.baseerah.application.infrastructure.gateway.genai;

import com.baseerah.application.infrastructure.gateway.genai.GenAiClient.ChatContext;
import java.util.function.Consumer;

/**
 * Optional streaming capability layered <em>beside</em> {@link GenAiClient} (DESIGN.md §9). Kept as a
 * separate interface on purpose: the Global Rule and Step 7.2 require the {@link GenAiClient} signature to
 * stay frozen, so token-by-token streaming is exposed here instead. A provider that can stream (the Phase-7
 * {@link RemoteGenAi}) implements both interfaces; the deterministic {@link MockGenAi} implements only
 * {@link GenAiClient}, so callers must {@code instanceof}-check before streaming and fall back to the
 * buffered {@link GenAiClient#chat} reply when the capability is absent.
 *
 * <p>Streaming is what delivers the DESIGN §9 <strong>time-to-first-token &lt; 1.0&nbsp;s</strong> target:
 * the first chunk is handed to {@code onChunk} the moment it arrives from the provider, rather than after the
 * whole reply is buffered.
 */
public interface StreamingGenAiClient {

    /**
     * Stream a grounded reply chunk-by-chunk.
     *
     * @param context the client's telemetry summary (same grounding {@link GenAiClient#chat} uses)
     * @param message the user's message
     * @param onChunk invoked once per text delta as it arrives, in order; never with {@code null}
     */
    void streamChat(ChatContext context, String message, Consumer<String> onChunk);
}
