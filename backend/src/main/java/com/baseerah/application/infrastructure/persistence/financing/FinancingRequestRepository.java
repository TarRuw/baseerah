package com.baseerah.application.infrastructure.persistence.financing;

import com.baseerah.domain.financing.FinancingStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link FinancingRequestJpaEntity}. A client's financing requests are keyed by
 * client: the full history (newest first) backs the consumer requests list the Rescue flow shows and polls,
 * and the latest-per-client finder backs the single-request read.
 */
public interface FinancingRequestRepository extends JpaRepository<FinancingRequestJpaEntity, UUID> {

    /** All of a client's financing requests, most recent first — the consumer history + polling source. */
    List<FinancingRequestJpaEntity> findByClient_IdOrderByCreatedAtDesc(UUID clientId);

    /** The most recently raised financing request for a client, or empty when they have never raised one. */
    Optional<FinancingRequestJpaEntity> findFirstByClient_IdOrderByCreatedAtDesc(UUID clientId);

    /**
     * The bank underwrite-stage queue (Phase 12 / Step 12.3): requests not yet underwritten
     * ({@code verdict IS NULL}) and still {@link FinancingStatus#OPEN}, oldest first. Constraining to
     * {@code OPEN} keeps a request the bank has already {@code DECLINED} at the underwrite stage (which leaves
     * {@code verdict} null) out of the queue, so a declined applicant never reappears to be underwritten again.
     */
    List<FinancingRequestJpaEntity> findByVerdictIsNullAndStatusOrderByCreatedAtAsc(FinancingStatus status);
}
