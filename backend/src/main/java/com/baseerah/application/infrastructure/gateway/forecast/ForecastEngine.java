package com.baseerah.application.infrastructure.gateway.forecast;

import com.baseerah.domain.forecast.ForecastResult;
import java.util.UUID;

/**
 * Predictive cash-flow forecasting gateway (FR-04, DESIGN.md §5.2). Projects a client's balance forward
 * from their seeded telemetry and flags the first liquidity deficit.
 *
 * <p>This is the <strong>stable seam</strong> for the forecasting strategy: the default
 * {@link HeuristicForecastEngine} is a pure-Java heuristic, but a Python Prophet/XGBoost sidecar can replace
 * it later without touching any caller — so the interface exposes only {@linkplain ForecastResult domain
 * types}, never a JPA entity and never a reference to a concrete implementation (Global Rule: engines stay
 * behind interfaces). It lives under {@code application/infrastructure/gateway} as a config-selected gateway
 * (not an outbound port): its collaborators are same-layer application services that call it directly.
 * Horizons flow through the single {@link #project} call: 30 days drives the Home chart, while 3/6/12-month
 * scenario shifts and the bank report just pass a larger {@code horizonDays}.
 */
public interface ForecastEngine {

    /**
     * Project {@code clientId}'s balance day-by-day over {@code [today, today + horizonDays]}.
     *
     * @param clientId    the client to forecast
     * @param horizonDays number of days to project forward (e.g. 30, 90, 180, 365)
     * @return the projected points, the first deficit date (nullable), and the minimum projected balance
     */
    ForecastResult project(UUID clientId, int horizonDays);

    /**
     * Invalidate any cached projection held for {@code clientId} (Phase 11 / Step 11.3). A client's declared
     * periodic expenses now feed the projection, so telemetry is no longer immutable at runtime: after a
     * declared expense is created, edited, or deactivated, the client's forecast must be recomputed rather than
     * served from a stale cache. Callers invoke this on any declared-expense write.
     *
     * <p>Default is a no-op: an engine with no cache (the pure {@link HeuristicForecastEngine}) has nothing to
     * evict; the {@code @Primary} {@link CachingForecastEngine} decorator overrides it to drop the client's
     * entries. A projection remains a pure function of telemetry + anchor day, so recomputation is always
     * correct — eviction only avoids serving a value computed before the write.
     *
     * @param clientId the client whose cached projections (all horizons/days) should be dropped
     */
    default void evict(UUID clientId) {
        // No cache to invalidate by default.
    }
}
