package com.baseerah.api.financing;

import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.application.financing.FinancingService;
import com.baseerah.shared.ApiResponse;
import com.baseerah.shared.Messages;
import com.baseerah.shared.NotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consumer financing RFP endpoints (Smart Rescue, DESIGN.md §5.4). Thin: validates the body ({@code @Valid}),
 * enforces per-client ownership via {@link OwnershipGuard}, delegates to {@link FinancingService}, and
 * projects the result through {@link FinancingWebMapper} inside the shared {@link ApiResponse} envelope — no
 * business logic here. The {@code {id}} path var is resolved (strict UUID) in the service (→ shared 404); the
 * {@code {rid}} request id and the body's proposal id are parsed here, mapping a malformed value to the shared
 * 404 to match the other client-scoped controllers.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class FinancingController {

    private final FinancingService financingService;
    private final OwnershipGuard ownershipGuard;
    private final Messages messages;

    public FinancingController(FinancingService financingService, OwnershipGuard ownershipGuard,
            Messages messages) {
        this.financingService = financingService;
        this.ownershipGuard = ownershipGuard;
        this.messages = messages;
    }

    /** {@code POST /{id}/financing/requests} — raise a request and fan it out to the chosen banks. */
    @PostMapping("/{id}/financing/requests")
    public ApiResponse<FinancingRequestResponse> create(
            @PathVariable String id, @Valid @RequestBody CreateFinancingRequest request) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(FinancingWebMapper.toResponse(
                financingService.createRequest(id, request.amount(), request.deficitInDays(), request.banks(),
                        request.origin(), request.purpose()),
                messages));
    }

    /** {@code GET /{id}/financing/requests} — the client's requests (newest first) with per-proposal impact. */
    @GetMapping("/{id}/financing/requests")
    public ApiResponse<List<FinancingRequestResponse>> list(@PathVariable String id) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(financingService.listReports(id).stream()
                .map(report -> FinancingWebMapper.toResponse(report, messages))
                .toList());
    }

    /** {@code POST /{id}/financing/requests/{rid}/accept} — accept a replied offer's terms; before/after. */
    @PostMapping("/{id}/financing/requests/{rid}/accept")
    public ApiResponse<FinancingChooseResponse> accept(
            @PathVariable String id, @PathVariable String rid,
            @Valid @RequestBody ChooseProposalRequest request) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(FinancingWebMapper.toChooseResponse(
                financingService.accept(id, requireUuid(rid), requireUuid(request.proposalId()))));
    }

    /** Parse a path/body id to a strict UUID, mapping a malformed value to the shared 404 (no leak). */
    private static UUID requireUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Financing resource not found: " + id);
        }
    }
}
