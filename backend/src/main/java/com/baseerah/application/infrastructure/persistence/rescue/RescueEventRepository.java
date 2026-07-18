package com.baseerah.application.infrastructure.persistence.rescue;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RescueEventJpaEntity}. Rescue events are an append-only audit log keyed
 * by client; the latest-per-client finder backs the "current recovery" read.
 */
public interface RescueEventRepository extends JpaRepository<RescueEventJpaEntity, UUID> {

    /** The most recently confirmed rescue for a client, or empty when the client has never been rescued. */
    Optional<RescueEventJpaEntity> findFirstByClient_IdOrderByCreatedAtDesc(UUID clientId);
}
