package com.baseerah.application.expense;

import java.math.BigDecimal;

/**
 * The application-layer carrier for a declared-expense write (create or update). Decouples
 * {@link DeclaredExpenseService} from the web request DTO: the api layer may depend on the application layer,
 * but not the reverse (the dependency rule — {@code LayeringTest}), so the controller translates its
 * {@code @Valid} request into this command before handing it to the service.
 *
 * <p>Carries only the four editable fields. {@code currency} (SAR) and {@code cadence} (MONTHLY) are
 * single-valued for now and defaulted on the JPA entity, so they are not part of the command.
 *
 * @param label      the user's own words for the expense (validated non-blank)
 * @param category   the raw category key (validated resolvable; {@code OTHER} is legal here)
 * @param amount     the recurring monthly amount in SAR (validated positive)
 * @param dayOfMonth the recurrence day of month (validated 1..31)
 */
public record DeclaredExpenseCommand(
        String label,
        String category,
        BigDecimal amount,
        int dayOfMonth) {
}
