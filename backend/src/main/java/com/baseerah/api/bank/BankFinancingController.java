package com.baseerah.api.bank;

import com.baseerah.api.bank.dto.DisbursementRowDto;
import com.baseerah.api.bank.dto.FinancingReplyRequest;
import com.baseerah.api.bank.dto.FinancingRequestRowDto;
import com.baseerah.application.bank.FinancingDisbursementService;
import com.baseerah.application.bank.FinancingReviewService;
import com.baseerah.shared.ApiResponse;
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
 * Bank Portal financing-review endpoints (the operator side of the Smart Rescue RFP flow). Gated to
 * {@code ROLE_BANK} by {@code SecurityConfig}'s {@code /api/v1/bank/**} rule — no per-client ownership (the
 * portal is one global operator). Thin: validates the body, delegates to {@link FinancingReviewService}, and
 * projects the result via {@link BankFinancingWebMapper} inside the shared envelope. Follows the
 * {@code BankController.decision} write pattern; a malformed {@code proposalId} maps to the shared 404.
 */
@RestController
@RequestMapping("/api/v1/bank")
public class BankFinancingController {

    private final FinancingReviewService financingReviewService;
    private final FinancingDisbursementService disbursementService;

    public BankFinancingController(FinancingReviewService financingReviewService,
            FinancingDisbursementService disbursementService) {
        this.financingReviewService = financingReviewService;
        this.disbursementService = disbursementService;
    }

    /** {@code GET /api/v1/bank/financing-requests} — the pending inbox (oldest first) with applicant context. */
    @GetMapping("/financing-requests")
    public ApiResponse<List<FinancingRequestRowDto>> inbox() {
        return ApiResponse.ok(financingReviewService.inbox().stream()
                .map(BankFinancingWebMapper::toRowDto)
                .toList());
    }

    /** {@code POST /api/v1/bank/financing-requests/{proposalId}/reply} — record the offered rate + term. */
    @PostMapping("/financing-requests/{proposalId}/reply")
    public ApiResponse<FinancingRequestRowDto> reply(
            @PathVariable String proposalId, @Valid @RequestBody FinancingReplyRequest request) {
        return ApiResponse.ok(BankFinancingWebMapper.toRowDto(
                financingReviewService.reply(proposalUuid(proposalId), request.rate(), request.term())));
    }

    /** {@code POST /api/v1/bank/financing-requests/{proposalId}/decline} — decline without an offer. */
    @PostMapping("/financing-requests/{proposalId}/decline")
    public ApiResponse<FinancingRequestRowDto> decline(@PathVariable String proposalId) {
        return ApiResponse.ok(
                BankFinancingWebMapper.toRowDto(financingReviewService.decline(proposalUuid(proposalId))));
    }

    /** {@code GET /api/v1/bank/financing-disbursements} — accepted offers awaiting a funding decision. */
    @GetMapping("/financing-disbursements")
    public ApiResponse<List<DisbursementRowDto>> disbursements() {
        return ApiResponse.ok(disbursementService.inbox().stream()
                .map(BankFinancingWebMapper::toDisbursementDto)
                .toList());
    }

    /** {@code POST /api/v1/bank/financing-disbursements/{proposalId}/disburse} — fund the accepted offer. */
    @PostMapping("/financing-disbursements/{proposalId}/disburse")
    public ApiResponse<DisbursementRowDto> disburse(@PathVariable String proposalId) {
        return ApiResponse.ok(BankFinancingWebMapper.toDisbursementDto(
                disbursementService.disburse(proposalUuid(proposalId))));
    }

    /** {@code POST /api/v1/bank/financing-disbursements/{proposalId}/decline} — decline at the final stage. */
    @PostMapping("/financing-disbursements/{proposalId}/decline")
    public ApiResponse<DisbursementRowDto> declineDisbursement(@PathVariable String proposalId) {
        return ApiResponse.ok(BankFinancingWebMapper.toDisbursementDto(
                disbursementService.decline(proposalUuid(proposalId))));
    }

    /** Parse the path {@code proposalId} to a strict UUID, mapping a malformed value to the shared 404. */
    private static UUID proposalUuid(String proposalId) {
        try {
            return UUID.fromString(proposalId);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Financing proposal not found: " + proposalId);
        }
    }
}
