package com.baseerah.api.stress;

import com.baseerah.application.stress.StressScoreService;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.shared.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Financial Stress Score endpoint (DESIGN.md §6). Thin: delegates to {@link StressScoreService}, maps the
 * domain result via {@link StressWebMapper}, and wraps it in the shared {@link ApiResponse} envelope — no
 * business logic and no persistence type here. The {@code id} is bound as a raw string and resolved (strict
 * UUID) inside the service, so an unknown/malformed id yields the shared 404 error envelope rather than a
 * binding error (matching the other client-scoped controllers).
 */
@RestController
@RequestMapping("/api/v1/clients")
public class StressScoreController {

    private final StressScoreService stressScoreService;
    private final OwnershipGuard ownershipGuard;

    public StressScoreController(StressScoreService stressScoreService, OwnershipGuard ownershipGuard) {
        this.stressScoreService = stressScoreService;
        this.ownershipGuard = ownershipGuard;
    }

    /** {@code GET /api/v1/clients/{id}/stress-score} — latest score, zone, gauge colour and sub-scores. */
    @GetMapping("/{id}/stress-score")
    public ApiResponse<StressScoreResponse> stressScore(@PathVariable String id) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(StressWebMapper.toResponse(stressScoreService.latestFor(id)));
    }
}
