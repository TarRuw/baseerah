package com.baseerah.domain.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outcome of a cash-flow projection (DESIGN.md §5.2): the full ordered {@code points} series, the first
 * date the balance is projected to go negative ({@code deficitDate}, {@code null} when it never crosses
 * zero), and the lowest balance reached over the horizon ({@code minProjectedBalance}).
 *
 * <p>A pure domain record — the immutable result the {@link BalanceProjection} core returns and the
 * {@code ForecastEngine} gateway hands back to the application layer. The web mapper projects it to the
 * API DTO; the JPA layer never sees it.
 *
 * @param points              ordered projected balances, {@code [today, today + horizonDays]} inclusive
 * @param deficitDate         first date the balance is projected negative, or {@code null}
 * @param minProjectedBalance lowest balance reached across the horizon
 */
public record ForecastResult(List<ForecastPoint> points, LocalDate deficitDate, BigDecimal minProjectedBalance) {
}
