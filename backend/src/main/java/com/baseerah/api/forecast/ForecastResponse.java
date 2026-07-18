package com.baseerah.api.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API view of a cash-flow forecast (FR-04, DESIGN.md §5.2, §6). Serialized inside the shared success
 * envelope by {@code GET /api/v1/clients/{id}/forecast} and consumed by the Step 2.3 Home 30-day chart.
 * The engine's domain {@code ForecastResult} never crosses the controller boundary — this immutable web
 * projection does; {@link ForecastWebMapper} maps between them (Global Rules).
 *
 * <p>{@code points} is the ordered day-by-day projected balance series; {@code deficitDate} is the first
 * day the balance is projected to go negative ({@code null} when it never does); {@code minProjectedBalance}
 * is the lowest balance reached over the horizon. {@code trend} is a <em>derived presentation value</em>
 * (like {@code color} on {@code StressScoreResponse}): the chart maps it straight to a theme colour token,
 * so the served line colour and the projected data can never disagree. The trend-derivation rule lives in
 * {@link ForecastWebMapper}.
 *
 * @param points              ordered projected balances, {@code [today, today + horizonDays]} inclusive
 * @param deficitDate         first date the balance is projected negative, or {@code null}
 * @param minProjectedBalance lowest balance reached across the horizon
 * @param trend               derived slope band driving the chart's line colour
 */
public record ForecastResponse(
        List<Point> points,
        LocalDate deficitDate,
        BigDecimal minProjectedBalance,
        Trend trend) {

    /** One projected day on the wire: the {@code date} and the {@code balance} reached that day. */
    public record Point(LocalDate date, BigDecimal balance) {
    }

    /** Slope band of the projection — the chart maps this to a line colour (DESIGN.md §7.1/§8). */
    public enum Trend {
        /** Balance rising over the horizon → success green. */
        UP,
        /** Balance roughly stable within the deadband → warning orange. */
        FLAT,
        /** Balance falling, or a deficit is projected → alert red. */
        DOWN
    }
}
