package com.baseerah.api.forecast;

import com.baseerah.application.forecast.ForecastService;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.shared.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cash-flow forecast endpoint (FR-04, DESIGN.md §6). Thin: delegates to {@link ForecastService}, maps the
 * domain result to the web DTO via {@link ForecastWebMapper}, and wraps it in the shared
 * {@link ApiResponse} envelope — no business logic here. The {@code id} is bound as a raw string and
 * resolved (strict UUID) inside the service, so an unknown/malformed id yields the shared 404 error
 * envelope (matching the other client-scoped controllers).
 *
 * <p>{@code @Validated} + {@code @Min/@Max} on {@code horizonDays} bound the horizon to {@code [1, 365]};
 * an out-of-range value becomes a {@code ConstraintViolationException} → the Step 0.4 handler renders the
 * shared {@code 400 VALIDATION_ERROR} envelope. The same query param serves the 30-day home chart and the
 * 3/6/12-month (90/180/365) scenario horizons.
 */
@RestController
@RequestMapping("/api/v1/clients")
@Validated
public class ForecastController {

    private final ForecastService forecastService;
    private final OwnershipGuard ownershipGuard;

    public ForecastController(ForecastService forecastService, OwnershipGuard ownershipGuard) {
        this.forecastService = forecastService;
        this.ownershipGuard = ownershipGuard;
    }

    /** {@code GET /api/v1/clients/{id}/forecast?horizonDays=30} — projected balances, deficit date, trend. */
    @GetMapping("/{id}/forecast")
    public ApiResponse<ForecastResponse> forecast(
            @PathVariable String id,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int horizonDays) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(ForecastWebMapper.toResponse(forecastService.forecastFor(id, horizonDays)));
    }
}
