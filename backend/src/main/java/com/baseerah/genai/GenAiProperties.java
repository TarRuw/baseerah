package com.baseerah.genai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound {@code baseerah.genai.*} configuration (DESIGN.md §2). Keeps every provider knob — selection, model,
 * base URL, API key, version, token cap — out of the code and behind env vars (see {@code application.yml}),
 * so no secret or endpoint is hardcoded (Step 7.2 acceptance).
 */
@ConfigurationProperties(prefix = "baseerah.genai")
public class GenAiProperties {

    /** {@code mock} (default, key-free) or {@code remote}; sourced from {@code GENAI_PROVIDER}. */
    private String provider = "mock";

    private Remote remote = new Remote();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Remote getRemote() {
        return remote;
    }

    public void setRemote(Remote remote) {
        this.remote = remote;
    }

    /** Settings for the {@link RemoteGenAi} adapter; only consulted when {@code provider=remote}. */
    public static class Remote {

        /** Messages-API model id. Defaults to the DESIGN §2 provider's current flagship. */
        private String model = "claude-opus-4-8";

        /** Provider base URL (no trailing {@code /v1/messages}). */
        private String baseUrl = "https://api.anthropic.com";

        /** API key from {@code GENAI_API_KEY}; blank/absent triggers the keyless fallback to the mock. */
        private String apiKey;

        /** Output-token cap for a single reply. */
        private int maxTokens = 1024;

        /** Provider API version header value. */
        private String version = "2023-06-01";

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
