package com.baseerah.api.expense;

import com.baseerah.api.expense.dto.DeclaredExpenseDto;
import com.baseerah.api.expense.dto.DeclaredExpenseRequest;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.application.expense.DeclaredExpenseService;
import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.shared.ApiResponse;
import com.baseerah.shared.ForbiddenException;
import com.baseerah.shared.NotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client-scoped CRUD for user-declared periodic expenses (Phase 11 / GitLab backend#1, DESIGN §6). Thin:
 * every method asserts ownership of the path {@code {id}} <em>first</em>, then delegates to
 * {@link DeclaredExpenseService} and maps the domain result to the wire view via
 * {@link DeclaredExpenseWebMapper} inside the shared {@link ApiResponse} envelope. Four routes:
 *
 * <ul>
 *   <li>{@code POST   /api/v1/clients/{id}/declared-expenses} — create → {@code 201}.</li>
 *   <li>{@code GET    /api/v1/clients/{id}/declared-expenses} — list the client's active expenses.</li>
 *   <li>{@code PUT    /api/v1/clients/{id}/declared-expenses/{expenseId}} — update in place.</li>
 *   <li>{@code DELETE /api/v1/clients/{id}/declared-expenses/{expenseId}} — soft-delete → {@code 204},
 *       idempotent.</li>
 * </ul>
 *
 * <p>Role gating ({@code ROLE_CONSUMER} on {@code /api/v1/clients/**}) is applied by {@code SecurityConfig};
 * {@link OwnershipGuard#assertOwns(String)} adds the per-row check so a consumer can only touch their own
 * client. The {@code {id}} is resolved to a {@code UUID} here (malformed → shared {@code 404}, matching the
 * sibling controllers). A malformed or foreign {@code {expenseId}} is refused as {@code 403} — the service
 * never distinguishes "unknown" from "not yours", so an id cannot be probed for existence.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class DeclaredExpenseController {

    private final DeclaredExpenseService declaredExpenseService;
    private final OwnershipGuard ownershipGuard;

    public DeclaredExpenseController(DeclaredExpenseService declaredExpenseService,
            OwnershipGuard ownershipGuard) {
        this.declaredExpenseService = declaredExpenseService;
        this.ownershipGuard = ownershipGuard;
    }

    /** {@code POST /api/v1/clients/{id}/declared-expenses} — declare a new recurring expense. */
    @PostMapping("/{id}/declared-expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeclaredExpenseDto> create(
            @PathVariable String id, @Valid @RequestBody DeclaredExpenseRequest request) {
        ownershipGuard.assertOwns(id);
        DeclaredExpense created =
                declaredExpenseService.create(clientId(id), DeclaredExpenseWebMapper.toCommand(request));
        return ApiResponse.ok(DeclaredExpenseWebMapper.toDto(created));
    }

    /** {@code GET /api/v1/clients/{id}/declared-expenses} — the client's active declared expenses. */
    @GetMapping("/{id}/declared-expenses")
    public ApiResponse<List<DeclaredExpenseDto>> list(@PathVariable String id) {
        ownershipGuard.assertOwns(id);
        List<DeclaredExpenseDto> expenses = declaredExpenseService.listActive(clientId(id)).stream()
                .map(DeclaredExpenseWebMapper::toDto)
                .toList();
        return ApiResponse.ok(expenses);
    }

    /** {@code PUT /api/v1/clients/{id}/declared-expenses/{expenseId}} — update in place. */
    @PutMapping("/{id}/declared-expenses/{expenseId}")
    public ApiResponse<DeclaredExpenseDto> update(@PathVariable String id, @PathVariable String expenseId,
            @Valid @RequestBody DeclaredExpenseRequest request) {
        ownershipGuard.assertOwns(id);
        DeclaredExpense updated = declaredExpenseService.update(
                clientId(id), expenseId(expenseId), DeclaredExpenseWebMapper.toCommand(request));
        return ApiResponse.ok(DeclaredExpenseWebMapper.toDto(updated));
    }

    /** {@code DELETE /api/v1/clients/{id}/declared-expenses/{expenseId}} — soft-delete (idempotent). */
    @DeleteMapping("/{id}/declared-expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @PathVariable String expenseId) {
        ownershipGuard.assertOwns(id);
        declaredExpenseService.deactivate(clientId(id), expenseId(expenseId));
    }

    /**
     * Parse the path {@code id} to a client {@code UUID}; a malformed value yields the shared {@code 404}
     * (matching every other client-scoped controller — the ownership guard has already accepted this id).
     */
    private static UUID clientId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Client not found: " + id);
        }
    }

    /**
     * Parse the {@code {expenseId}} to a {@code UUID}; a malformed value is refused as {@code 403} rather
     * than {@code 404}/{@code 400}, so it is indistinguishable from a foreign or unknown id (no probing).
     */
    private static UUID expenseId(String expenseId) {
        try {
            return UUID.fromString(expenseId);
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException("You do not have permission to access this resource.");
        }
    }
}
