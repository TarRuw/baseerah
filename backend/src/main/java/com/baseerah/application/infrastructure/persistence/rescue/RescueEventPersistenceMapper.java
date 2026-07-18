package com.baseerah.application.infrastructure.persistence.rescue;

import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.domain.rescue.RescueAssessment;
import com.baseerah.domain.rescue.RescueOption;
import com.baseerah.domain.rescue.RescueOutcome;

/**
 * Maps the domain values of a confirmed rescue to the {@link RescueEventJpaEntity} the service persists
 * (step 10.6) — the write-side counterpart to the read mappers in the other slices. Static and pure: it
 * assembles the append-only audit row from the assessment (what triggered the rescue), the chosen option,
 * and the recovery outcome, so {@code RescueService} never constructs the JPA entity by hand.
 *
 * <p>The {@code @ManyToOne} target is still the {@code ClientJpaEntity} entity (relocated in the CRUD slice, step
 * 10.9), so the resolved client is threaded in from the shell rather than mapped from a domain value.
 */
public final class RescueEventPersistenceMapper {

    private RescueEventPersistenceMapper() {
    }

    /**
     * Build the {@code rescue_events} row for a confirmed rescue.
     *
     * @param client     the resolved owner of the rescue (the {@code @ManyToOne} target)
     * @param assessment the assessment that triggered the rescue (shortfall + lead time)
     * @param option     the chosen bridge (its type is persisted as {@code option_chosen})
     * @param outcome    the before/after stress score of the recovery
     * @return a new, unsaved audit-log entity
     */
    public static RescueEventJpaEntity toJpaEntity(ClientJpaEntity client, RescueAssessment assessment,
            RescueOption option, RescueOutcome outcome) {
        return new RescueEventJpaEntity(client, assessment.predictedShortfall(), assessment.deficitInDays(),
                option.type(), outcome.scoreBefore(), outcome.scoreAfter());
    }
}
