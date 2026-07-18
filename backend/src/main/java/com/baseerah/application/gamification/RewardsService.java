package com.baseerah.application.gamification;

import com.baseerah.application.infrastructure.persistence.gamification.ChallengeJpaEntity;
import com.baseerah.application.infrastructure.persistence.gamification.ChallengeProgressJpaEntity;
import com.baseerah.application.infrastructure.persistence.gamification.ChallengeProgressRepository;
import com.baseerah.application.infrastructure.persistence.gamification.ChallengeRepository;
import com.baseerah.application.infrastructure.persistence.gamification.RewardsLedgerJpaEntity;
import com.baseerah.application.infrastructure.persistence.gamification.RewardsLedgerRepository;
import com.baseerah.domain.gamification.RewardTier;
import com.baseerah.domain.gamification.RewardsView;
import com.baseerah.domain.gamification.TierRule;
import com.baseerah.shared.ConflictException;
import com.baseerah.shared.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Akhtar-Points rewards application service (FR-09/10, DESIGN.md §5.6). Owns the client's points balance —
 * the running sum of the {@code rewards_ledger} — the derived {@link RewardTier}, and the guarded
 * challenge-claim transition. Backs the Step 5.2 balance and claim endpoints and the Step 5.3 Goals screen's
 * points card.
 *
 * <p><strong>Claim is the only mutator of the ledger</strong> and is doubly guarded: it awards a challenge's
 * points exactly once, and only when the challenge is both <em>complete</em> ({@code pct >= 100}) and
 * <em>unclaimed</em>. An incomplete or already-claimed claim is rejected and writes nothing, so points can
 * never be granted twice or early. Balance and tier are always recomputed from the ledger (never stored) via
 * {@link TierRule#forBalance(int)}, so they cannot drift.
 */
@Service
public class RewardsService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final RewardsLedgerRepository rewardsLedgerRepository;
    private final Clock clock;

    @Autowired
    public RewardsService(ChallengeRepository challengeRepository,
            ChallengeProgressRepository challengeProgressRepository,
            RewardsLedgerRepository rewardsLedgerRepository) {
        this(challengeRepository, challengeProgressRepository, rewardsLedgerRepository, Clock.systemUTC());
    }

    RewardsService(ChallengeRepository challengeRepository,
            ChallengeProgressRepository challengeProgressRepository,
            RewardsLedgerRepository rewardsLedgerRepository, Clock clock) {
        this.challengeRepository = challengeRepository;
        this.challengeProgressRepository = challengeProgressRepository;
        this.rewardsLedgerRepository = rewardsLedgerRepository;
        this.clock = clock;
    }

    /** The client's current Akhtar-Points balance (sum of their ledger) and its derived tier. */
    @Transactional(readOnly = true)
    public RewardsView summaryFor(UUID clientId) {
        int balance = rewardsLedgerRepository.sumPointsByClientId(clientId);
        return new RewardsView(balance, TierRule.forBalance(balance));
    }

    /**
     * Claim the reward for a completed challenge (FR-10). Awards the challenge's {@code reward_points} to the
     * client exactly once: flips the progress to {@code claimed} at now, appends one {@code rewards_ledger}
     * row, and returns the updated balance and tier.
     *
     * @param clientId    the client claiming (must own the challenge)
     * @param challengeId the completed challenge being claimed
     * @return the awarded points and the client's new balance/tier
     * @throws NotFoundException  if the challenge does not exist or does not belong to {@code clientId}
     * @throws ConflictException  if the challenge is not complete, or has already been claimed (→ 409)
     */
    @Transactional
    public ClaimResult claimChallenge(UUID clientId, UUID challengeId) {
        ChallengeJpaEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new NotFoundException("Challenge not found: " + challengeId));
        if (!challenge.getClient().getId().equals(clientId)) {
            // Don't leak another client's challenge — treat as not found for this client.
            throw new NotFoundException("Challenge not found: " + challengeId);
        }

        ChallengeProgressJpaEntity progress = challengeProgressRepository.findByChallenge_Id(challengeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Challenge has no progress to claim: " + challengeId));
        if (progress.isClaimed()) {
            throw new ConflictException("Challenge already claimed: " + challengeId);
        }
        if (!progress.isComplete()) {
            throw new ConflictException("Challenge not complete: " + challengeId);
        }

        progress.markClaimed(clock.instant());
        challengeProgressRepository.save(progress);

        int awarded = challenge.getRewardPoints();
        rewardsLedgerRepository.save(new RewardsLedgerJpaEntity(challenge.getClient(), awarded,
                "Claimed challenge: " + challenge.getCode()));

        int balance = rewardsLedgerRepository.sumPointsByClientId(clientId);
        return new ClaimResult(challengeId, awarded, balance, TierRule.forBalance(balance));
    }

    /** The outcome of a successful claim: what was awarded and the client's resulting balance/tier. */
    public record ClaimResult(UUID challengeId, int awardedPoints, int balance, RewardTier tier) {
    }
}
