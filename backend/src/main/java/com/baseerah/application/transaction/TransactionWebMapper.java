package com.baseerah.application.transaction;

import com.baseerah.api.transaction.dto.TransactionDto;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionJpaEntity;

/**
 * Maps {@link TransactionJpaEntity} entities to {@link TransactionDto} projections. Pure and stateless.
 * Reads scalar fields only; never touches the lazy {@code account} association, so mapping a page of
 * history triggers no N+1 loads.
 *
 * <p>Lives in the application layer (not api): it is the anemic-slice edge where a persistence entity
 * becomes a web DTO, so the api layer never depends on a JPA entity (Phase 10.11 dependency rule).
 */
public final class TransactionWebMapper {

    private TransactionWebMapper() {
    }

    public static TransactionDto toDto(TransactionJpaEntity tx) {
        return new TransactionDto(
                tx.getId(),
                tx.getDirection(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getRawDescription(),
                tx.getDescriptionCleansed(),
                tx.getCategory(),
                tx.getCategoryConfidence(),
                tx.getBookingDate(),
                tx.getClosingBalance());
    }
}
