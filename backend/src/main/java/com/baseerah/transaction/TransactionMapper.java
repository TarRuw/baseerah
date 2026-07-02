package com.baseerah.transaction;

import com.baseerah.transaction.dto.TransactionDto;

/**
 * Maps {@link Transaction} entities to {@link TransactionDto} projections. Pure and stateless. Reads
 * scalar fields only; never touches the lazy {@code account} association, so mapping a page of history
 * triggers no N+1 loads.
 */
public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransactionDto toDto(Transaction tx) {
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
