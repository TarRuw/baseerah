package com.baseerah.genai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active {@link GenAiClient} bean from configuration (DESIGN.md §2). The provider is chosen by
 * {@code baseerah.genai.provider}, which defaults to the {@code GENAI_PROVIDER} environment variable and
 * falls back to {@code mock} (see {@code application.yml}):
 * <ul>
 *   <li>{@code mock} (default) → {@link MockGenAi}: deterministic, telemetry-grounded, <strong>needs no API
 *       key</strong> (DESIGN §9 reliability).</li>
 *   <li>{@code remote} → the reserved Phase-7 adapter (Step 7.2). The switch is wired now, but the remote
 *       call is not implemented here — the bean resolves to {@link RemoteGenAiPlaceholder}, which fails fast
 *       if invoked before Phase 7.</li>
 * </ul>
 *
 * <p>Exactly one bean is created, so callers inject a single {@link GenAiClient} and never see a concrete
 * type (Global Rule). {@code mock} is also the {@code matchIfMissing} default, so the mock resolves even if
 * the property is absent entirely.
 */
@Configuration
public class GenAiConfig {

    /** Default, key-free provider. Active when {@code baseerah.genai.provider} is {@code mock} or unset. */
    @Bean
    @ConditionalOnProperty(name = "baseerah.genai.provider", havingValue = "mock", matchIfMissing = true)
    public GenAiClient mockGenAiClient() {
        return new MockGenAi();
    }

    /** Reserved remote provider. Active when {@code baseerah.genai.provider=remote}; implemented in Phase 7. */
    @Bean
    @ConditionalOnProperty(name = "baseerah.genai.provider", havingValue = "remote")
    public GenAiClient remoteGenAiClient() {
        return new RemoteGenAiPlaceholder();
    }

    /**
     * Placeholder for the not-yet-built {@code RemoteGenAi} adapter (Step 7.2). It satisfies the
     * {@code remote} switch so the wiring is complete and testable today, but fails fast if actually called —
     * the real streaming remote call is out of scope for this step (DESIGN §2, §9).
     */
    static final class RemoteGenAiPlaceholder implements GenAiClient {

        private static final String NOT_IMPLEMENTED =
                "RemoteGenAi is reserved for Phase 7 (Step 7.2). Set GENAI_PROVIDER=mock to use the "
                        + "deterministic mock provider.";

        @Override
        public ChatReply chat(ChatContext context, String message) {
            throw new UnsupportedOperationException(NOT_IMPLEMENTED);
        }

        @Override
        public InvoiceParseResult parseInvoice(byte[] image) {
            throw new UnsupportedOperationException(NOT_IMPLEMENTED);
        }
    }
}
