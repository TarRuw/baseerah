package com.baseerah.gamification;

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
}
