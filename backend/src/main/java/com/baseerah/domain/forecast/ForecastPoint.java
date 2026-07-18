package com.baseerah.domain.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One projected day of a cash-flow forecast: the client's balance on {@code date} after applying that
 * day's cash flows (DESIGN.md §5.2). A pure domain value — no framework types cross this boundary, so a
 * Python forecasting sidecar or a caching decorator can produce it just as the heuristic core does.
 *
 * @param date             the projected day
 * @param projectedBalance the balance reached on {@code date}
 */
public record ForecastPoint(LocalDate date, BigDecimal projectedBalance) {
}
