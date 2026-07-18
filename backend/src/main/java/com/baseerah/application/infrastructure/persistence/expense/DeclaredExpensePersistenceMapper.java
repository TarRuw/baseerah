package com.baseerah.application.infrastructure.persistence.expense;

import com.baseerah.application.expense.DeclaredExpenseCommand;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.domain.expense.DeclaredExpense;
import com.baseerah.domain.kernel.Category;

/**
 * Maps between the {@link DeclaredExpenseJpaEntity} persistence shape and the pure
 * {@code domain.expense.DeclaredExpense}, keeping the JPA↔domain translation at the application edge (Phase
 * 11 is a rule-bearing slice — the service never hands a JPA entity to the web layer). Static and pure,
 * mirroring the other persistence mappers.
 *
 * <p>{@link #toDomain} resolves the stored {@code category} text to a {@link Category} via
 * {@link Category#resolve(String)}; for declared expenses {@link Category#OTHER} is a legal stored value, so
 * a resolved {@code OTHER} here is meaningful, not a feed-parsing miss.
 */
public final class DeclaredExpensePersistenceMapper {

    private DeclaredExpensePersistenceMapper() {
    }

    /** Project a persisted row to the pure domain record Step 11.3's calculators consume. */
    public static DeclaredExpense toDomain(DeclaredExpenseJpaEntity entity) {
        return new DeclaredExpense(
                entity.getId(),
                entity.getLabel(),
                Category.resolve(entity.getCategory()),
                entity.getAmount(),
                entity.getDayOfMonth(),
                entity.isActive());
    }

    /**
     * Build a new, unsaved {@code declared_expenses} row for {@code client} from a validated create command.
     * {@code currency}/{@code cadence}/{@code active} default on the entity (SAR / MONTHLY / true).
     */
    public static DeclaredExpenseJpaEntity toJpaEntity(ClientJpaEntity client, DeclaredExpenseCommand command) {
        return new DeclaredExpenseJpaEntity(
                client, command.label(), command.category(), command.amount(), command.dayOfMonth());
    }
}
