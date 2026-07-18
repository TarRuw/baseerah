package com.baseerah.api.account;

import com.baseerah.api.account.dto.AccountDto;
import com.baseerah.application.account.AccountService;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.shared.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account endpoints (DESIGN.md §6). Thin: delegates to {@link AccountService}. The {@code id} is bound
 * as a raw string and resolved (strict UUID) inside the service, so an unknown/malformed id yields the
 * shared 404 envelope rather than a binding error.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class AccountController {

    private final AccountService accountService;
    private final OwnershipGuard ownershipGuard;

    public AccountController(AccountService accountService, OwnershipGuard ownershipGuard) {
        this.accountService = accountService;
        this.ownershipGuard = ownershipGuard;
    }

    /** {@code GET /api/v1/clients/{id}/accounts} — the client's linked accounts. */
    @GetMapping("/{id}/accounts")
    public ApiResponse<List<AccountDto>> accounts(@PathVariable String id) {
        ownershipGuard.assertOwns(id);
        return ApiResponse.ok(accountService.accountsForClient(id));
    }
}
