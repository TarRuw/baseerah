package com.baseerah.api.bank;

import com.baseerah.api.bank.dto.LoanRequestRowDto;
import com.baseerah.api.bank.dto.UnderwritingReportDto;
import com.baseerah.application.bank.BankComplianceMapper;
import com.baseerah.application.bank.BankReportResult;
import com.baseerah.application.bank.LoanRequestReviewService;
import com.baseerah.domain.bank.RiskPolicy;
import com.baseerah.shared.ApiResponse;
import com.baseerah.shared.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank Portal <strong>underwrite-stage</strong> endpoints over the unified loan-request model (Phase 12 /
 * Step 12.3) — the request-model successor to the retired FR-08 {@code /applicants} approve/decline surface.
 * Gated to {@code ROLE_BANK} by {@code SecurityConfig}'s {@code /api/v1/bank/**} rule (one global operator, no
 * per-client ownership). Thin: it delegates to {@link LoanRequestReviewService} and projects the result inside
 * the shared envelope; the underwrite response's compliance surface (tokenized accounts + NDMO residency) is
 * resolved through {@link BankComplianceMapper} exactly as the old bank report did, so no raw account id ever
 * reaches the wire (Global Rules, DESIGN §9).
 *
 * <ul>
 *   <li>{@code GET  /loan-requests?stage=underwrite} — the un-underwritten queue (oldest first).</li>
 *   <li>{@code POST /loan-requests/{id}/underwrite} — run + stamp the §5.5 report, return the full report.</li>
 *   <li>{@code POST /loan-requests/{id}/decline} — reject the applicant (request → {@code DECLINED}).</li>
 * </ul>
 *
 * <p>A malformed path {@code id} maps to the shared 404, matching {@link BankFinancingController}.
 */
@RestController
@RequestMapping("/api/v1/bank")
public class BankLoanRequestController {

    /** The only supported {@code stage} today: pricing/disbursement have their own endpoints. */
    private static final String STAGE_UNDERWRITE = "underwrite";

    private final LoanRequestReviewService reviewService;
    private final BankComplianceMapper complianceMapper;

    public BankLoanRequestController(LoanRequestReviewService reviewService,
            BankComplianceMapper complianceMapper) {
        this.reviewService = reviewService;
        this.complianceMapper = complianceMapper;
    }

    /**
     * {@code GET /api/v1/bank/loan-requests?stage=underwrite} — the underwrite-stage queue: consumer requests
     * awaiting a risk report, oldest first. {@code stage} defaults to {@code underwrite} (the pricing and
     * disbursement stages are served by {@link BankFinancingController}); an unknown stage yields an empty list.
     */
    @GetMapping("/loan-requests")
    public ApiResponse<List<LoanRequestRowDto>> queue(
            @RequestParam(name = "stage", defaultValue = STAGE_UNDERWRITE) String stage) {
        if (!STAGE_UNDERWRITE.equalsIgnoreCase(stage)) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(reviewService.underwriteQueue().stream()
                .map(BankWebMapper::toLoanRequestRowDto)
                .toList());
    }

    /**
     * {@code POST /api/v1/bank/loan-requests/{id}/underwrite} — underwrite the request: compute + stamp the
     * §5.5 report and return it (verdict, stamina, DTI, income stability, 12-month PD, cash-flow chart, and the
     * compliance stamp keyed by the request's client).
     */
    @PostMapping("/loan-requests/{id}/underwrite")
    public ApiResponse<UnderwritingReportDto> underwrite(@PathVariable String id) {
        BankReportResult result = reviewService.underwrite(requestUuid(id));
        RiskPolicy policy = result.policy();
        List<String> tokens = complianceMapper.accountTokensFor(result.clientRef(), policy);
        String residency = complianceMapper.residencyMarker(policy);
        boolean exportAllowed = complianceMapper.exportAllowed(policy);
        return ApiResponse.ok(
                BankWebMapper.toReportDto(result.report(), result.cashFlow(), tokens, residency, exportAllowed));
    }

    /** {@code POST /api/v1/bank/loan-requests/{id}/decline} — reject the applicant (request → {@code DECLINED}). */
    @PostMapping("/loan-requests/{id}/decline")
    public ApiResponse<LoanRequestRowDto> decline(@PathVariable String id) {
        return ApiResponse.ok(BankWebMapper.toLoanRequestRowDto(reviewService.decline(requestUuid(id))));
    }

    /** Parse the path {@code id} to a strict UUID, mapping a malformed value to the shared 404. */
    private static UUID requestUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Loan request not found: " + id);
        }
    }
}
