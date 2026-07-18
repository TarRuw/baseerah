package com.baseerah.application.infrastructure.persistence.gamification;

import com.baseerah.domain.gamification.ChallengeView;
import java.math.BigDecimal;

/**
 * Maps the gamification persistence entities to the pure domain {@link ChallengeView} the application/web
 * layers consume — the {@code gamification} analogue of {@code StressPersistenceMapper}. Pure and static:
 * same-layer collaborators call it directly and the JPA entity never crosses the boundary (Global Rules).
 * The view carries message <em>keys</em> and raw SAR amounts; localization is the web mapper's job.
 */
public final class ChallengePersistenceMapper {

    private ChallengePersistenceMapper() {
    }

    /**
     * Project a challenge joined with its (0..1) progress row into the domain view. A {@code null} progress
     * row (generation has not created one yet) reads as zero progress, unclaimed and unclaimable — matching
     * the prior mapping.
     */
    public static ChallengeView toView(ChallengeJpaEntity challenge, ChallengeProgressJpaEntity progress) {
        BigDecimal current = progress == null ? BigDecimal.ZERO : progress.getCurrentValue();
        int pct = progress == null ? 0 : progress.getPct();
        boolean claimed = progress != null && progress.isClaimed();
        boolean claimable = progress != null && progress.isClaimable();
        return new ChallengeView(challenge.getId(), challenge.getIcon(), challenge.getTitle(),
                challenge.getSubtitle(), challenge.getTextArgs(), challenge.getCategoryTrigger(),
                challenge.getRewardPoints(), pct, current, challenge.getTargetValue(), claimable, claimed);
    }
}
