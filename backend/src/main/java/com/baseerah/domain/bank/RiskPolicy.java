package com.baseerah.domain.bank;

import java.time.Instant;

/**
 * The bank-wide risk policy (DESIGN.md §4.2, §7.7) as a pure domain value — the immutable view the
 * persistence layer maps its singleton {@code risk_policy} row to. {@code staminaFloor} /
 * {@code autoDeclineThreshold} are the §5.5 auto-decline guardrails the {@link UnderwritingRule} overlays
 * on a computed verdict and the portfolio screening applies; {@code ndmoResidency} and {@code tokenization}
 * are the compliance toggles the web-layer compliance surface honours; {@code samaLastSync} stamps the last
 * SAMA Open-Banking sync shown in Settings ({@code null} = never synced).
 *
 * @param staminaFloor         minimum stamina to pass policy (0–100); below it → auto-decline
 * @param autoDeclineThreshold forecast-DTI percentage at/above which to auto-decline (0–200)
 * @param ndmoResidency        NDMO data-residency compliance toggle
 * @param tokenization         SAMA account-tokenization compliance toggle
 * @param samaLastSync         last SAMA Open-Banking sync timestamp, or {@code null}
 */
public record RiskPolicy(
        int staminaFloor,
        int autoDeclineThreshold,
        boolean ndmoResidency,
        boolean tokenization,
        Instant samaLastSync) {
}
