package com.baseerah.api.stress;

import com.baseerah.domain.stress.Zone;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * API view of a client's latest Financial Stress Score (DESIGN.md §5.1, §6). Serialized inside the shared
 * success envelope by {@code GET /api/v1/clients/{id}/stress-score} and consumed by the Home/Radar gauge.
 * The domain {@link com.baseerah.domain.stress.StressScore} value is projected here by
 * {@link StressWebMapper}; the JPA entity never crosses the controller boundary (Global Rules).
 *
 * <p>{@code score} is the integer 0–100 index and {@code zone} its band; {@code color} is the zone's gauge
 * hex from DESIGN.md §8, resolved in the web mapper because the colour belongs to the API response — the
 * values are kept identical to the Flutter theme tokens so the served colour and the rendered zone never
 * disagree. The three sub-scores are on the same 0–100 healthiness scale (higher = healthier).
 *
 * <p>The three sub-scores are all on the same 0–100 <em>healthiness</em> scale where <strong>higher =
 * healthier</strong>. The field names say so explicitly ({@code spendingHealth}, {@code obligationHealth})
 * so a reader never mistakes a high value for "spending fast" or "heavily indebted" — the value is always
 * "how healthy," not "how much."
 *
 * @param score            integer 0–100 stress index (higher = healthier)
 * @param zone             health band for {@code score} (DESIGN.md §5.1)
 * @param color            the zone's gauge hex (DESIGN.md §8)
 * @param spendingHealth   spending-health sub-score, 0–100 (higher = spending well within income)
 * @param incomeConsistency income-consistency sub-score, 0–100 (higher = steadier income)
 * @param obligationHealth obligation-health sub-score, 0–100 (higher = lighter obligations + more buffer)
 * @param asOfDate         the day this snapshot was computed for
 */
public record StressScoreResponse(
        int score,
        Zone zone,
        String color,
        BigDecimal spendingHealth,
        BigDecimal incomeConsistency,
        BigDecimal obligationHealth,
        LocalDate asOfDate) {
}
