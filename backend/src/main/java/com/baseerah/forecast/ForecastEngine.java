package com.baseerah.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Predictive cash-flow forecasting (FR-04, DESIGN.md §5.2). Projects a client's balance forward from
 * their seeded telemetry and flags the first liquidity deficit.
 *
 * <p>This is the <strong>stable seam</strong> for the forecasting strategy: the default
 * {@link HeuristicForecast} is a pure-Java heuristic, but a Python Prophet/XGBoost sidecar can replace it
 * later without touching any caller — so the interface exposes only domain types (records below), never a
 * JPA entity and never a reference to a concrete implementation (Global Rule: engines stay behind
 * interfaces). Horizons flow through the single {@link #project} call: 30 days drives the Home chart,
 * while 3/6/12-month scenario shifts and the bank report just pass a larger {@code horizonDays}.
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

    /** One projected day: the client's balance on {@code date} after applying that day's cash flows. */
    record ForecastPoint(LocalDate date, BigDecimal projectedBalance) {
    }

    /**
     * Outcome of a projection: the full ordered {@code points} series, the first date the balance is
     * projected to go negative ({@code deficitDate}, {@code null} when it never crosses zero), and the
     * lowest balance reached over the horizon ({@code minProjectedBalance}).
     */
    record ForecastResult(List<ForecastPoint> points, LocalDate deficitDate, BigDecimal minProjectedBalance) {
    }
}
