package com.baseerah.application.infrastructure.persistence.gamification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link RewardsLedgerJpaEntity}. The ledger is append-only and the client's
 * balance is its running sum, so the primary read is the aggregate {@link #sumPointsByClientId(UUID)} —
 * computed in the database rather than by summing an in-memory list, keeping the balance read cheap as the
 * journal grows.
 */
public interface RewardsLedgerRepository extends JpaRepository<RewardsLedgerJpaEntity, UUID> {

    /** A client's ledger entries (audit/history view). */
    List<RewardsLedgerJpaEntity> findByClient_Id(UUID clientId);

    /**
     * The client's Akhtar-Points balance: the sum of every {@code points_delta} for the client, or {@code 0}
     * when the client has no ledger entries yet ({@code COALESCE} guards the empty-sum {@code NULL}).
     */
    @Query("select coalesce(sum(e.pointsDelta), 0) from RewardsLedgerJpaEntity e where e.client.id = :clientId")
    int sumPointsByClientId(@Param("clientId") UUID clientId);
}
