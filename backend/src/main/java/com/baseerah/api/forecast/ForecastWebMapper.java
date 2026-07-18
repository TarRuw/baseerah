package com.baseerah.api.forecast;

import com.baseerah.domain.forecast.ForecastPoint;
import com.baseerah.domain.forecast.ForecastResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * Maps the domain {@link ForecastResult} to its API view {@link ForecastResponse}, resolving the derived
 * presentation {@code trend} here in the web layer (like the gauge colour on {@code StressWebMapper}). Pure
 * and static — the controller calls it directly; no framework or JPA types cross this mapper.
 *
 * <p><strong>Trend derivation rule (documented for reuse by the Phase 6 bank report):</strong>
 * <ol>
 *   <li>A projected deficit dominates — when {@code deficitDate != null} the line is by definition heading
 *       below zero, so the trend is {@link ForecastResponse.Trend#DOWN} (alert red) regardless of the
 *       endpoints.</li>
 *   <li>Otherwise compare the last projected balance against the first. If it rises by more than
 *       {@link #FLAT_BAND_FRACTION} of the (absolute) starting balance → {@link ForecastResponse.Trend#UP}
 *       (green); if it falls by more than that band → {@link ForecastResponse.Trend#DOWN} (red); within the
 *       band → {@link ForecastResponse.Trend#FLAT} (orange). The relative deadband keeps near-flat
 *       projections from flickering UP/DOWN on noise.</li>
 * </ol>
 */
public final class ForecastWebMapper {

    /** Reference move (fraction of the starting balance) that counts as a real trend at the reference horizon. */
    private static final BigDecimal FLAT_BAND_FRACTION = new BigDecimal("0.025");

    /** Horizon (days) the {@link #FLAT_BAND_FRACTION} reference is calibrated for; the band scales from here. */
    private static final int REFERENCE_HORIZON_DAYS = 30;

    private ForecastWebMapper() {
    }

    /** Map a domain {@link ForecastResult} to its API view, deriving {@link #deriveTrend the trend}. */
    public static ForecastResponse toResponse(ForecastResult result) {
        List<ForecastResponse.Point> points = result.points().stream()
                .map(p -> new ForecastResponse.Point(p.date(), p.projectedBalance()))
                .toList();
        return new ForecastResponse(
                points, result.deficitDate(), result.minProjectedBalance(), deriveTrend(result));
    }

    /** Derive the trend band from a projection — see the class-level rule. Package-visible for unit tests. */
    static ForecastResponse.Trend deriveTrend(ForecastResult result) {
        // A projected deficit dominates: the line is heading below zero (DESIGN §5.2/§7.1).
        if (result.deficitDate() != null) {
            return ForecastResponse.Trend.DOWN;
        }
        List<ForecastPoint> pts = result.points();
        if (pts.size() < 2) {
            return ForecastResponse.Trend.FLAT;
        }
        BigDecimal first = pts.get(0).projectedBalance();
        BigDecimal last = pts.get(pts.size() - 1).projectedBalance();
        BigDecimal delta = last.subtract(first);
        // Horizon-scaled deadband: grow the "flat" band with the projection length (~sqrt of the day count)
        // off the reference fraction, so a small drift over a short horizon reads FLAT rather than a false
        // alarming DOWN, while a real decline over many months still trips UP/DOWN.
        int horizonDays = pts.size() - 1;
        double horizonScale = Math.sqrt(horizonDays / (double) REFERENCE_HORIZON_DAYS);
        BigDecimal band = first.abs().multiply(FLAT_BAND_FRACTION)
                .multiply(BigDecimal.valueOf(horizonScale));
        if (delta.compareTo(band) > 0) {
            return ForecastResponse.Trend.UP;
        }
        if (delta.compareTo(band.negate()) < 0) {
            return ForecastResponse.Trend.DOWN;
        }
        return ForecastResponse.Trend.FLAT;
    }
}
