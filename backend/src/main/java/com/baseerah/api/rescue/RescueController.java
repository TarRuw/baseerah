package com.baseerah.api.rescue;

import com.baseerah.application.rescue.RescueService;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.domain.rescue.RescueOption;
import com.baseerah.domain.rescue.RescueOptionType;
import com.baseerah.shared.ApiResponse;
import com.baseerah.shared.Messages;
import com.baseerah.shared.NotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smart Rescue endpoints (FR-06/07, DESIGN.md §6). Thin: delegates to {@link RescueService} and maps the
 * domain result to the wire view via {@link RescueWebMapper} inside the shared {@link ApiResponse} envelope —
 * the deficit detection, option sizing, and recovery curve all stay in the application/domain layers
 * (ORCHESTRATION). Two routes mirror DESIGN §6:
 *
 * <ul>
 *   <li>{@code GET  /api/v1/clients/{id}/rescue} — predicted shortfall + two bridge options, or an explicit
 *       no-deficit state for a healthy persona (never a 4xx for "no deficit").</li>
 *   <li>{@code POST /api/v1/clients/{id}/rescue/confirm} — confirm an option, returning the before/after
 *       score recovery; the service persists the {@code rescue_events} row.</li>
 * </ul>
 *
 * <p>The path {@code id} is bound as a raw string and resolved to a client-scoped {@code UUID} here; a
 * malformed or unknown id yields the shared {@code 404 NOT_FOUND} envelope, matching every other
 * client-scoped controller. A malformed request body ({@code option} not one of the two enum names) yields
 * the shared {@code 400 VALIDATION_ERROR} envelope. The {@link Messages} resolver is threaded into the web
 * mapper so the option labels/details and confirmation message render in the request locale.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class RescueController {

    private final RescueService rescueService;
    private final OwnershipGuard ownershipGuard;
    private final Messages messages;

    public RescueController(RescueService rescueService, OwnershipGuard ownershipGuard, Messages messages) {
        this.rescueService = rescueService;
        this.ownershipGuard = ownershipGuard;
        this.messages = messages;
    }

    /** {@code GET /api/v1/clients/{id}/rescue} — shortfall + options, or a clean no-deficit state. */
    @GetMapping("/{id}/rescue")
    public ApiResponse<RescueResponse> rescue(@PathVariable String id) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(RescueWebMapper.toResponse(rescueService.assess(clientId(id)), messages));
    }

    /** {@code POST /api/v1/clients/{id}/rescue/confirm} — confirm a bridge; returns before/after recovery. */
    @PostMapping("/{id}/rescue/confirm")
    public ApiResponse<RescueConfirmResponse> confirm(
            @PathVariable String id, @Valid @RequestBody RescueConfirmRequest request) {
        ownershipGuard.assertOwns(id);
        UUID clientId = clientId(id);
        RescueOptionType type = RescueOptionType.valueOf(request.option()); // @Pattern guarantees a valid name
        RescueOption chosen = rescueService.assess(clientId).options().stream()
                .filter(option -> option.type() == type)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "No " + type + " rescue option available for client " + id));
        RescueConfirmResponse response = RescueWebMapper.toConfirmResponse(
                rescueService.confirm(clientId, chosen), chosen, messages);
        return ApiResponse.ok(response);
    }

    /**
     * Parse the path {@code id} to a client-scoped {@code UUID}, mapping a malformed value to the shared
     * {@code 404} (the service applies the same strict-UUID contract for an unknown-but-well-formed id). This
     * bridges the controller's raw string to the {@code UUID}-typed service without leaking a 500.
     */
    private static UUID clientId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Client not found: " + id);
        }
    }
}
