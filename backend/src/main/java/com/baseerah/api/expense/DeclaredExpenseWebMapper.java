package com.baseerah.api.expense;

import com.baseerah.api.expense.dto.DeclaredExpenseDto;
import com.baseerah.api.expense.dto.DeclaredExpenseRequest;
import com.baseerah.application.expense.DeclaredExpenseCommand;
import com.baseerah.domain.expense.DeclaredExpense;

/**
 * Translates between the declared-expense web shapes and the layers behind them (Phase 11): the inbound
 * {@link DeclaredExpenseRequest} → an application {@link DeclaredExpenseCommand}, and the domain
 * {@link DeclaredExpense} → the outbound {@link DeclaredExpenseDto}. Pure and static — the controller calls
 * it directly, keeping the web edge thin and the domain locale/framework-free (mirrors {@code RescueWebMapper}).
 *
 * <p>The product is SAR-only and MONTHLY-only today, so the DTO's {@code currency}/{@code cadence} are those
 * constants rather than fields on the domain record (which a calculator never branches on).
 */
public final class DeclaredExpenseWebMapper {

    private static final String CURRENCY_SAR = "SAR";
    private static final String CADENCE_MONTHLY = "MONTHLY";

    private DeclaredExpenseWebMapper() {
    }

    /** Adapt a validated write request to the application command the service consumes. */
    public static DeclaredExpenseCommand toCommand(DeclaredExpenseRequest request) {
        return new DeclaredExpenseCommand(
                request.label(), request.category(), request.amount(), request.dayOfMonth());
    }

    /** Project a domain declared expense to its wire view. */
    public static DeclaredExpenseDto toDto(DeclaredExpense expense) {
        return new DeclaredExpenseDto(
                expense.id(),
                expense.label(),
                expense.category().name(),
                expense.amount(),
                CURRENCY_SAR,
                CADENCE_MONTHLY,
                expense.dayOfMonth(),
                expense.active());
    }
}
