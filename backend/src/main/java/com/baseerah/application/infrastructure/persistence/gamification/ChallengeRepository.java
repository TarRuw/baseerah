package com.baseerah.application.infrastructure.persistence.gamification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ChallengeJpaEntity}. Challenges are keyed by client; the
 * {@code (clientId, code)} finder backs idempotent generation (upsert on the same stable {@code code}).
 * Finders traverse the {@code client} relation ({@code client.id}), matching the codebase's explicit-path
 * convention.
 */
public interface ChallengeRepository extends JpaRepository<ChallengeJpaEntity, UUID> {

    /** All of a client's generated challenges (Step 5.2 list endpoint). */
    List<ChallengeJpaEntity> findByClient_Id(UUID clientId);

    /** A client's challenge with the given stable {@code code}, or empty — the idempotency lookup. */
    Optional<ChallengeJpaEntity> findByClient_IdAndCode(UUID clientId, String code);
}
