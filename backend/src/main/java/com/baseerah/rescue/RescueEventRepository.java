package com.baseerah.rescue;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RescueEvent}. Rescue events are an append-only audit log keyed by
 * client; the latest-per-client finder backs the Step 4.2 "current recovery" read.
 */
public interface RescueEventRepository extends JpaRepository<RescueEvent, UUID> {

    /** The most recently confirmed rescue for a client, or empty when the client has never been rescued. */
    Optional<RescueEvent> findFirstByClient_IdOrderByCreatedAtDesc(UUID clientId);
}
