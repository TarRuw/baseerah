package com.baseerah.genai;

import com.baseerah.common.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the single active {@link GenAiClient} bean from configuration (DESIGN.md §2), once at wiring time —
 * never in a controller. Exactly one bean is created, so callers inject one {@link GenAiClient} and never see
 * a concrete type (Global Rule).
 *
 * <ul>
 *   <li>{@code baseerah.genai.provider=remote} <strong>with an API key present</strong> → {@link RemoteGenAi}
 *       (streams; DESIGN §9 time-to-first-token &lt; 1.0&nbsp;s).</li>
 *   <li>{@code provider=remote} but the key is missing/blank → logs a clear warning and <strong>falls back to
 *       {@link MockGenAi}</strong>, so the demo still runs offline / without keys (DESIGN §9 reliability)
 *       rather than failing startup or requests.</li>
 *   <li>anything else (including unset) → {@link MockGenAi}, the deterministic, key-free default.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(GenAiProperties.class)
public class GenAiConfig {

    private static final Logger log = LoggerFactory.getLogger(GenAiConfig.class);

    @Bean
    public GenAiClient genAiClient(GenAiProperties properties, Messages messages) {
        if ("remote".equalsIgnoreCase(properties.getProvider())) {
            GenAiProperties.Remote remote = properties.getRemote();
            if (hasText(remote.getApiKey())) {
                log.info("GenAI provider=remote — using RemoteGenAi (model={}, baseUrl={}).",
                        remote.getModel(), remote.getBaseUrl());
                return new RemoteGenAi(remote.getBaseUrl(), remote.getModel(), remote.getApiKey(),
                        remote.getMaxTokens(), remote.getVersion());
            }
            log.warn("GENAI_PROVIDER=remote but no API key is set (GENAI_API_KEY blank/absent) — "
                    + "falling back to MockGenAi so the demo runs offline (DESIGN §9).");
        }
        return new MockGenAi(messages);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
