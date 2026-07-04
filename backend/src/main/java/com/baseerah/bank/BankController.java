package com.baseerah.bank;

import com.baseerah.bank.dto.ApplicantDto;
import com.baseerah.bank.dto.DecisionRequest;
import com.baseerah.bank.dto.PortfolioDto;
import com.baseerah.bank.dto.RiskPolicyDto;
import com.baseerah.bank.dto.UnderwritingReportDto;
import com.baseerah.common.ApiResponse;
import com.baseerah.common.NotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank Portal endpoints (FR-08, DESIGN.md §6). Thin: every route delegates to {@link BankService} and wraps
 * the result in the shared {@link ApiResponse} envelope — the underwriting, portfolio aggregation, and
 * risk-policy logic all stay in the service (Global Rules). Five capabilities under {@code /api/v1/bank}:
 *
 * <ul>
 *   <li>{@code GET  /applicants} — the underwriting queue.</li>
 *   <li>{@code POST /applicants/{id}/report} — generate + persist the predictive report (with cash-flow chart).</li>
 *   <li>{@code POST /applicants/{id}/decision} — record an approve/decline decision.</li>
 *   <li>{@code GET  /portfolio} — KPIs + monitoring rows.</li>
 *   <li>{@code GET}/{@code PUT} {@code /risk-policy} — read/update the singleton risk policy.</li>
 * </ul>
 *
 * <p>The {@code {id}} path variable is bound as a raw string and parsed to a strict {@code UUID} here; a
 * malformed value yields the shared {@code 404 NOT_FOUND} envelope, matching the client-scoped controllers.
 * A malformed decision body ({@code decision} not APPROVE/DECLINE) yields the shared
 * {@code 400 VALIDATION_ERROR} envelope, and an out-of-range risk-policy {@code PUT} the same.
 */
@RestController
@RequestMapping("/api/v1/bank")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    /** {@code GET /api/v1/bank/applicants} — the underwriting queue (oldest first). */
    @GetMapping("/applicants")
    public ApiResponse<List<ApplicantDto>> applicants() {
        return ApiResponse.ok(bankService.queue());
    }

    /** {@code POST /api/v1/bank/applicants/{id}/report} — generate + persist the predictive report. */
    @PostMapping("/applicants/{id}/report")
    public ApiResponse<UnderwritingReportDto> report(@PathVariable String id) {
        return ApiResponse.ok(bankService.report(applicationId(id)));
    }

    /** {@code POST /api/v1/bank/applicants/{id}/decision} — record an approve/decline decision. */
    @PostMapping("/applicants/{id}/decision")
    public ApiResponse<ApplicantDto> decision(
            @PathVariable String id, @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok(bankService.decide(applicationId(id), request.toDecision()));
    }

    /** {@code GET /api/v1/bank/portfolio} — 4 KPIs + monitoring rows. */
    @GetMapping("/portfolio")
    public ApiResponse<PortfolioDto> portfolio() {
        return ApiResponse.ok(bankService.portfolio());
    }

    /** {@code GET /api/v1/bank/risk-policy} — read the singleton risk policy. */
    @GetMapping("/risk-policy")
    public ApiResponse<RiskPolicyDto> riskPolicy() {
        return ApiResponse.ok(bankService.riskPolicy());
    }

    /** {@code PUT /api/v1/bank/risk-policy} — update the singleton and return the persisted policy. */
    @PutMapping("/risk-policy")
    public ApiResponse<RiskPolicyDto> updateRiskPolicy(@Valid @RequestBody RiskPolicyDto request) {
        return ApiResponse.ok(bankService.updateRiskPolicy(request));
    }

    /**
     * Parse the path {@code id} to an application {@code UUID}, mapping a malformed value to the shared
     * {@code 404} (the service applies the same not-found contract for an unknown-but-well-formed id).
     */
    private static UUID applicationId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Loan application not found: " + id);
        }
    }
}
