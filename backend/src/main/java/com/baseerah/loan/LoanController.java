package com.baseerah.loan;

import com.baseerah.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loan-affordability endpoint (FR-05, DESIGN.md §6). Thin: validates the request body ({@code @Valid}) and
 * delegates to {@link LoanCalculator}, wrapping the result in the shared {@link ApiResponse} envelope — no
 * business logic here. The {@code id} is bound as a raw string and resolved (strict UUID) inside the
 * calculator, so an unknown/malformed id yields the shared 404 envelope, matching the other client-scoped
 * controllers; a malformed body yields the Step 0.4 {@code 400 VALIDATION_ERROR} envelope.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class LoanController {

    private final LoanCalculator loanCalculator;

    public LoanController(LoanCalculator loanCalculator) {
        this.loanCalculator = loanCalculator;
    }

    /** {@code POST /api/v1/clients/{id}/loan-simulate} — instalment, total, DTI, verdict, projected score. */
    @PostMapping("/{id}/loan-simulate")
    public ApiResponse<LoanSimulateResponse> simulate(
            @PathVariable String id, @Valid @RequestBody LoanSimulateRequest request) {
        return ApiResponse.ok(loanCalculator.simulate(id, request));
    }
}
