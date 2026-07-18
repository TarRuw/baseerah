package com.baseerah.api.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wire view of a declared periodic expense (Phase 11 / GitLab backend#1). Projected from the
 * {@code domain.expense.DeclaredExpense} record by {@code DeclaredExpenseWebMapper}. {@code category} is the
 * resolved canonical key (upper-case constant name); {@code currency}/{@code cadence} are the invariant
 * SAR/MONTHLY the product supports today, echoed so the Flutter client (Steps 11.4–11.5) need not assume them.
 *
 * @param id         the declared-expense identity
 * @param label      the user's own words for the expense
 * @param category   the resolved category key (e.g. {@code UTILITIES}, {@code OTHER})
 * @param amount     the recurring monthly amount in SAR
 * @param currency   always {@code SAR} today
 * @param cadence    always {@code MONTHLY} today
 * @param dayOfMonth the recurrence day of month (1..31)
 * @param active     whether the expense is live (soft-deleted rows are never returned by the list read)
 */
public record DeclaredExpenseDto(
        UUID id,
        String label,
        String category,
        BigDecimal amount,
        String currency,
        String cadence,
        int dayOfMonth,
        boolean active) {
}
