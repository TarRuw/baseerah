package com.baseerah.forecast;

import com.baseerah.forecast.ForecastEngine.ForecastPoint;
import com.baseerah.forecast.ForecastEngine.ForecastResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API view of a cash-flow forecast (FR-04, DESIGN.md §5.2, §6). Serialized inside the shared success
 * envelope by {@code GET /api/v1/clients/{id}/forecast} and consumed by the Step 2.3 Home 30-day chart.
 * The engine's {@link ForecastResult} (a domain record) never crosses the controller boundary — this
 * immutable projection does (Global Rules).
 *
 * <p>{@code points} is the ordered day-by-day projected balance series; {@code deficitDate} is the first
 * day the balance is projected to go negative ({@code null} when it never does); {@code minProjectedBalance}
 * is the lowest balance reached over the horizon. {@code trend} is a <em>derived presentation value</em>
 * (like {@code color} on {@code StressScoreResponse}): the chart maps it straight to a theme colour token,
 * so the served line colour and the projected data can never disagree.
 *
 * <p><strong>Trend derivation rule (documented for reuse by the Phase 6 bank report):</strong>
 * <ol>
 *   <li>A projected deficit dominates — when {@code deficitDate != null} the line is by definition heading
 *       below zero, so the trend is {@link Trend#DOWN} (alert red) regardless of the endpoints.</li>
 *   <li>Otherwise compare the last projected balance against the first. If it rises by more than
 *       {@link #FLAT_BAND_FRACTION} of the (absolute) starting balance → {@link Trend#UP} (green); if it
 *       falls by more than that band → {@link Trend#DOWN} (red); within the band → {@link Trend#FLAT}
 *       (orange). The relative deadband keeps near-flat projections from flickering UP/DOWN on noise.</li>
 * </ol>
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

    /** A move must exceed this fraction of the starting balance to count as UP/DOWN rather than FLAT. */
    private static final BigDecimal FLAT_BAND_FRACTION = new BigDecimal("0.01");

    /** Map an engine {@link ForecastResult} to its API view, deriving {@link #trend} per the class rule. */
    public static ForecastResponse from(ForecastResult result) {
        List<Point> points = result.points().stream()
                .map(p -> new Point(p.date(), p.projectedBalance()))
                .toList();
        return new ForecastResponse(
                points, result.deficitDate(), result.minProjectedBalance(), deriveTrend(result));
    }

    /** Derive the trend band from a projection — see the class-level rule. Package-visible for unit tests. */
    static Trend deriveTrend(ForecastResult result) {
        // A projected deficit dominates: the line is heading below zero (DESIGN §5.2/§7.1).
        if (result.deficitDate() != null) {
            return Trend.DOWN;
        }
        List<ForecastPoint> pts = result.points();
        if (pts.isEmpty()) {
            return Trend.FLAT;
        }
        BigDecimal first = pts.get(0).projectedBalance();
        BigDecimal last = pts.get(pts.size() - 1).projectedBalance();
        BigDecimal delta = last.subtract(first);
        BigDecimal band = first.abs().multiply(FLAT_BAND_FRACTION);
        if (delta.compareTo(band) > 0) {
            return Trend.UP;
        }
        if (delta.compareTo(band.negate()) < 0) {
            return Trend.DOWN;
        }
        return Trend.FLAT;
    }
}
