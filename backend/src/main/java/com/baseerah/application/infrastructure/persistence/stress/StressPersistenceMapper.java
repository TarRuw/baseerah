package com.baseerah.application.infrastructure.persistence.stress;

import com.baseerah.domain.stress.StressScore;

/**
 * Projects the stress persistence snapshot to the domain: {@link StressScoreJpaEntity} → the immutable
 * domain {@link StressScore}. The {@code Transaction → LedgerEntry} mapping the pure calculators consume
 * now lives once in
 * {@code application.infrastructure.persistence.transaction.TransactionPersistenceMapper} (step 10.9), so
 * every rule-bearing slice shares a single ledger mapping. Pure and static — same-layer collaborators
 * call it directly.
 */
public final class StressPersistenceMapper {

    private StressPersistenceMapper() {
    }

    /** Project a persisted snapshot to its immutable domain view. */
    public static StressScore toDomain(StressScoreJpaEntity entity) {
        return new StressScore(
                entity.getScore(),
                entity.getZone(),
                entity.getSpendingVelocity(),
                entity.getIncomeConsistency(),
                entity.getLiabilityRatio(),
                entity.getAsOfDate());
    }
}
