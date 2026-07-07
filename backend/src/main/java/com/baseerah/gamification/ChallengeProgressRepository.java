package com.baseerah.gamification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link ChallengeProgress}. There is exactly one progress row per challenge
 * (DB-enforced), so the by-challenge finder returns at most one.
 */
public interface ChallengeProgressRepository extends JpaRepository<ChallengeProgress, UUID> {

    /** The progress row for a challenge, or empty when generation has not created one yet. */
    Optional<ChallengeProgress> findByChallenge_Id(UUID challengeId);

    /**
     * The progress rows for a set of challenges in one query — the batch form of {@link #findByChallenge_Id}
     * that lets the challenge list resolve every goal's progress without an N+1 (Step 7.3). Backed by the
     * unique {@code challenge_id} on {@code challenge_progress}.
     */
    List<ChallengeProgress> findByChallenge_IdIn(Collection<UUID> challengeIds);
}
