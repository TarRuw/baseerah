package com.baseerah.bank.dto;

import com.baseerah.bank.RiskPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;

/**
 * Wire view of the bank-wide risk policy singleton (DESIGN.md §4.2, §7.7), used both as the
 * {@code GET /api/v1/bank/risk-policy} response and the {@code PUT} request body — the Step 6.4 Settings
 * screen reads it and writes the edited copy back, and a {@code PUT} then {@code GET} round-trips every
 * field. Maps to/from the {@link RiskPolicy} entity, which never crosses the boundary (Global Rules).
 *
 * <p>The bounds are enforced on the {@code PUT} body via {@code @Valid} → the shared
 * {@code 400 VALIDATION_ERROR} envelope (they are inert on the response path): {@code staminaFloor} is a
 * 0–100 endurance score and {@code autoDeclineThreshold} is a forecast-DTI percentage (the §5.5 BAD band
 * starts at 71%), capped at the {@link com.baseerah.bank.UnderwritingService} DTI ceiling of 200%. The two
 * boolean flags are the NDMO data-residency and SAMA tokenization compliance toggles; {@code samaLastSync}
 * is the last Open-Banking sync timestamp shown in Settings ({@code null} = never synced).
 *
 * @param staminaFloor         minimum stamina to pass policy (0–100)
 * @param autoDeclineThreshold forecast-DTI percentage at/above which to auto-decline (0–200)
 * @param ndmoResidency        NDMO data-residency compliance toggle
 * @param tokenization         SAMA account-tokenization compliance toggle
 * @param samaLastSync         last SAMA Open-Banking sync timestamp, or {@code null}
 */
public record RiskPolicyDto(
        @Min(0) @Max(100) int staminaFloor,
        @Min(0) @Max(200) int autoDeclineThreshold,
        boolean ndmoResidency,
        boolean tokenization,
        Instant samaLastSync) {

    /** Map the persisted {@link RiskPolicy} singleton to its wire view. */
    public static RiskPolicyDto from(RiskPolicy policy) {
        return new RiskPolicyDto(policy.getStaminaFloor(), policy.getAutoDeclineThreshold(),
                policy.isNdmoResidency(), policy.isTokenization(), policy.getSamaLastSync());
    }
}
