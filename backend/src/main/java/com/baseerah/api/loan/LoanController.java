package com.baseerah.api.loan;

import com.baseerah.application.loan.LoanService;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.domain.loan.LoanQuote;
import com.baseerah.shared.ApiResponse;
import com.baseerah.shared.Messages;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loan-affordability endpoint (FR-05, DESIGN.md §6). Thin: validates the request body ({@code @Valid}),
 * enforces per-client ownership, delegates the math to {@link LoanService}, and projects the domain
 * {@link LoanQuote} to its API view via {@link LoanWebMapper} inside the shared {@link ApiResponse} envelope —
 * no business logic here. The {@code id} is bound as a raw string and resolved (strict UUID) inside the
 * service, so an unknown/malformed id yields the shared 404 envelope, matching the other client-scoped
 * controllers; a malformed body yields the Step 0.4 {@code 400 VALIDATION_ERROR} envelope.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class LoanController {

    private final LoanService loanService;
    private final OwnershipGuard ownershipGuard;
    private final Messages messages;

    public LoanController(LoanService loanService, OwnershipGuard ownershipGuard, Messages messages) {
        this.loanService = loanService;
        this.ownershipGuard = ownershipGuard;
        this.messages = messages;
    }

    /** {@code POST /api/v1/clients/{id}/loan-simulate} — instalment, total, DTI, verdict, projected score. */
    @PostMapping("/{id}/loan-simulate")
    public ApiResponse<LoanSimulateResponse> simulate(
            @PathVariable String id, @Valid @RequestBody LoanSimulateRequest request) {
        ownershipGuard.assertOwns(id);
        LoanQuote quote = loanService.simulate(id, request.principal(), request.rate(), request.term());
        return ApiResponse.ok(LoanWebMapper.toResponse(quote, messages));
    }
}
