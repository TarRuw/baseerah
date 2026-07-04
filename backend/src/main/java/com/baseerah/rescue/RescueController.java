package com.baseerah.rescue;

import com.baseerah.common.ApiResponse;
import com.baseerah.common.NotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smart Rescue endpoints (FR-06/07, DESIGN.md §6). Thin: delegates to {@link RescueService} and wraps the
 * result in the shared {@link ApiResponse} envelope — the deficit detection, option sizing, and recovery
 * curve all stay in the service (ORCHESTRATION). Two routes mirror DESIGN §6:
 *
 * <ul>
 *   <li>{@code GET  /api/v1/clients/{id}/rescue} — predicted shortfall + two bridge options, or an explicit
 *       no-deficit state for a healthy persona (never a 4xx for "no deficit").</li>
 *   <li>{@code POST /api/v1/clients/{id}/rescue/confirm} — confirm an option, returning the before/after
 *       score recovery; the service persists the {@code rescue_events} row.</li>
 * </ul>
 *
 * <p>The path {@code id} is bound as a raw string and resolved to a client-scoped {@code UUID} here (Step 4.1's
 * service methods take a {@code UUID}); a malformed or unknown id yields the shared {@code 404 NOT_FOUND}
 * envelope, matching every other client-scoped controller. A malformed request body ({@code option} not one
 * of the two enum names) yields the shared {@code 400 VALIDATION_ERROR} envelope.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class RescueController {

    private final RescueService rescueService;

    public RescueController(RescueService rescueService) {
        this.rescueService = rescueService;
    }

    /** {@code GET /api/v1/clients/{id}/rescue} — shortfall + options, or a clean no-deficit state. */
    @GetMapping("/{id}/rescue")
    public ApiResponse<RescueResponse> rescue(@PathVariable String id) {
        return ApiResponse.ok(RescueResponse.from(rescueService.assess(clientId(id))));
    }

    /** {@code POST /api/v1/clients/{id}/rescue/confirm} — confirm a bridge; returns before/after recovery. */
    @PostMapping("/{id}/rescue/confirm")
    public ApiResponse<RescueConfirmResponse> confirm(
            @PathVariable String id, @Valid @RequestBody RescueConfirmRequest request) {
        UUID clientId = clientId(id);
        RescueOptionType type = RescueOptionType.valueOf(request.option()); // @Pattern guarantees a valid name
        RescueOption chosen = rescueService.assess(clientId).options().stream()
                .filter(option -> option.type() == type)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "No " + type + " rescue option available for client " + id));
        return ApiResponse.ok(RescueConfirmResponse.from(rescueService.confirm(clientId, chosen)));
    }

    /**
     * Parse the path {@code id} to a client-scoped {@code UUID}, mapping a malformed value to the shared
     * {@code 404} (the service applies the same strict-UUID contract for an unknown-but-well-formed id). This
     * bridges the controller's raw string to Step 4.1's {@code UUID}-typed service without leaking a 500.
     */
    private static UUID clientId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Client not found: " + id);
        }
    }
}
