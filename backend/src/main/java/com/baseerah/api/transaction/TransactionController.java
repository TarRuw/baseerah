package com.baseerah.api.transaction;

import com.baseerah.api.transaction.dto.TransactionDto;
import com.baseerah.application.transaction.TransactionService;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.shared.ApiResponse;
import com.baseerah.shared.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transaction-history endpoint (DESIGN.md §6). Thin: reads pagination params, delegates ordering and
 * clamping to {@link TransactionService}, and wraps the page in the shared {@link PageResponse} /
 * {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class TransactionController {

    private final TransactionService transactionService;
    private final OwnershipGuard ownershipGuard;

    public TransactionController(TransactionService transactionService, OwnershipGuard ownershipGuard) {
        this.transactionService = transactionService;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * {@code GET /api/v1/clients/{id}/transactions?page=&size=} — newest-first history, paginated.
     * Defaults to page 0, size {@link TransactionService#DEFAULT_PAGE_SIZE}; size is capped in the
     * service.
     */
    @GetMapping("/{id}/transactions")
    public ApiResponse<PageResponse<TransactionDto>> transactions(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TransactionService.DEFAULT_PAGE_SIZE) int size) {
        ownershipGuard.assertOwns(id);
        Page<TransactionDto> result = transactionService.transactionsForClient(id, page, size);
        return ApiResponse.ok(PageResponse.of(result));
    }
}
