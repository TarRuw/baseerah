package com.baseerah.api.cashflow;

import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.application.cashflow.CashflowSummary;
import com.baseerah.application.cashflow.CashflowSummaryService;
import com.baseerah.shared.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Average monthly cash-flow endpoint (DESIGN.md §6) backing the Home "average income / spending" cards.
 * Thin: enforces per-client ownership, delegates to {@link CashflowSummaryService}, and wraps the result in
 * the shared {@link ApiResponse} envelope — no business logic here. The {@code id} is bound raw and resolved
 * (strict UUID) inside the service, so an unknown/malformed id yields the shared 404 envelope, matching the
 * other client-scoped controllers.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class CashflowSummaryController {

    private final CashflowSummaryService cashflowSummaryService;
    private final OwnershipGuard ownershipGuard;

    public CashflowSummaryController(CashflowSummaryService cashflowSummaryService,
            OwnershipGuard ownershipGuard) {
        this.cashflowSummaryService = cashflowSummaryService;
        this.ownershipGuard = ownershipGuard;
    }

    /** {@code GET /api/v1/clients/{id}/cashflow-summary} — average monthly income and spending (SAR). */
    @GetMapping("/{id}/cashflow-summary")
    public ApiResponse<CashflowSummaryResponse> cashflowSummary(@PathVariable String id) {
        ownershipGuard.assertOwns(id);
        CashflowSummary summary = cashflowSummaryService.summarize(id);
        return ApiResponse.ok(
                new CashflowSummaryResponse(summary.avgMonthlyIncome(), summary.avgMonthlyExpense()));
    }
}
