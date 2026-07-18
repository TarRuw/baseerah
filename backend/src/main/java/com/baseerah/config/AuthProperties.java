package com.baseerah.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bound {@code baseerah.auth.*} configuration for phone + OTP authentication (DESIGN.md §12, Phase 9).
 * This step only defines the knobs; Step 9.2 consumes them to verify OTPs and mint JWTs.
 *
 * <p>{@code mockOtp} and {@code jwtSecret} carry <strong>no in-code defaults</strong> — they are supplied
 * from the environment via {@code application.yml} ({@code BASEERAH_AUTH_MOCK_OTP} /
 * {@code BASEERAH_AUTH_JWT_SECRET}), and the application fails fast at startup if either is missing.
 * NOTE: the bundled OTP provider is still a mock; replace it with a real gateway before production use.
 *
 * @param mockOtp the fixed OTP the mock provider accepts; from {@code BASEERAH_AUTH_MOCK_OTP}.
 * @param jwtSecret HS256 signing key; must be at least 32 bytes. From {@code BASEERAH_AUTH_JWT_SECRET}.
 * @param jwtTtl access-token lifetime; override with {@code BASEERAH_AUTH_JWT_TTL} (ISO-8601, e.g. {@code PT12H}).
 */
@ConfigurationProperties(prefix = "baseerah.auth")
public record AuthProperties(
        String mockOtp,
        String jwtSecret,
        @DefaultValue("PT12H") Duration jwtTtl) {
}
