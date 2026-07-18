package com.baseerah.application.infrastructure.persistence.transaction;

import com.baseerah.domain.kernel.LedgerEntry;
import java.util.List;

/**
 * The single mapping from the shared {@link TransactionJpaEntity} to the domain {@link LedgerEntry} the
 * pure calculators consume. Consolidated here (step 10.9) so every rule-bearing slice
 * (stress/forecast/loan/rescue/gamification/bank) shares one {@code Transaction → LedgerEntry} mapping —
 * the domain never sees a raw feed string or a JPA type. The mapping reproduces the entity's category
 * resolution ({@link TransactionJpaEntity#resolveCategory()}). All methods are pure and static —
 * same-layer collaborators call them directly.
 */
public final class TransactionPersistenceMapper {

    private TransactionPersistenceMapper() {
    }

    /** The domain ledger view of a single transaction, resolving its raw category to a typed one. */
    public static LedgerEntry toLedgerEntry(TransactionJpaEntity tx) {
        return new LedgerEntry(
                // Reads the id straight off the (lazy) account proxy — no initialisation, so no N+1.
                tx.getAccount().getId(),
                tx.getDirection(),
                tx.getAmount(),
                tx.resolveCategory(),
                tx.getDescriptionCleansed(),
                tx.getBookingDate(),
                tx.getClosingBalance());
    }

    /** Map a whole transaction window to ledger entries for a domain calculator. */
    public static List<LedgerEntry> toLedgerEntries(List<TransactionJpaEntity> window) {
        return window.stream().map(TransactionPersistenceMapper::toLedgerEntry).toList();
    }
}
