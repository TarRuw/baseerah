package com.baseerah.api.category;

import com.baseerah.domain.kernel.Category;
import com.baseerah.shared.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reference-data endpoint for the declared-expense category picker (Phase 11 / GitLab backend#3). Unlike
 * every other {@code /api/v1} route this is <strong>client-agnostic</strong> — the vocabulary is the same
 * for every user — so there is deliberately <em>no</em> {@code OwnershipGuard} and no {@code {id}} in the
 * path; it merely requires a valid token (see the explicit {@code /api/v1/categories/**} matcher in
 * {@code SecurityConfig}), for either role.
 *
 * <p>Returns stable enum <strong>keys</strong>, never localized strings: the API stays locale-free and the
 * Flutter language toggle re-renders the picker instantly from its ARB labels (Step 11.4) with no refetch.
 * The trade-off — a new backend constant is invisible until a matching ARB key ships — is an accepted,
 * visible coupling.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    /**
     * {@code GET /api/v1/categories/expense} — the expense-only category keys the picker may offer: the
     * sixteen spending categories plus {@code OTHER} (locked decision #3); the four income keys are absent.
     */
    @GetMapping("/expense")
    public ApiResponse<List<String>> expenseCategories() {
        List<String> keys = Category.declarableExpenseCategories().stream()
                .map(Category::name)
                .toList();
        return ApiResponse.ok(keys);
    }
}
