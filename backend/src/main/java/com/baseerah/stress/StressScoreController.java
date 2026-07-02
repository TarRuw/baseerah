package com.baseerah.stress;

import com.baseerah.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Financial Stress Score endpoint (DESIGN.md §6). Thin: delegates to {@link StressScoreService} and wraps
 * the result in the shared {@link ApiResponse} envelope — no business logic here. The {@code id} is bound
 * as a raw string and resolved (strict UUID) inside the service, so an unknown/malformed id yields the
 * shared 404 error envelope rather than a binding error (matching the other client-scoped controllers).
 */
@RestController
@RequestMapping("/api/v1/clients")
public class StressScoreController {

    private final StressScoreService stressScoreService;

    public StressScoreController(StressScoreService stressScoreService) {
        this.stressScoreService = stressScoreService;
    }

    /** {@code GET /api/v1/clients/{id}/stress-score} — latest score, zone, gauge colour and sub-scores. */
    @GetMapping("/{id}/stress-score")
    public ApiResponse<StressScoreResponse> stressScore(@PathVariable String id) {
        return ApiResponse.ok(stressScoreService.latestFor(id));
    }
}
