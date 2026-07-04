package com.baseerah.gamification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Challenge}. Challenges are keyed by client; the {@code (clientId, code)}
 * finder backs idempotent generation (upsert on the same stable {@code code}). Finders traverse the
 * {@code client} relation ({@code client.id}), matching the codebase's explicit-path convention.
 */
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    /** All of a client's generated challenges (Step 5.2 list endpoint). */
    List<Challenge> findByClient_Id(UUID clientId);

    /** A client's challenge with the given stable {@code code}, or empty — the idempotency lookup. */
    Optional<Challenge> findByClient_IdAndCode(UUID clientId, String code);
}
