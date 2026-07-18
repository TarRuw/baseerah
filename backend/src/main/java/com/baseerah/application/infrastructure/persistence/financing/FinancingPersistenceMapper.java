package com.baseerah.application.infrastructure.persistence.financing;

import com.baseerah.domain.financing.FinancingProposal;
import com.baseerah.domain.financing.FinancingRequest;
import java.util.List;

/**
 * Projects the financing JPA entities to their pure domain values (the read-side counterpart to the write
 * helpers on the entities). Static and pure — so {@code FinancingService} and the web layer never see a JPA
 * entity (the dependency rule: JPA entities never cross out of the application layer).
 */
public final class FinancingPersistenceMapper {

    private FinancingPersistenceMapper() {
    }

    /** Project a request and its eagerly-loaded proposals to the domain {@link FinancingRequest}. */
    public static FinancingRequest toDomain(FinancingRequestJpaEntity entity) {
        List<FinancingProposal> proposals = entity.getProposals().stream()
                .map(FinancingPersistenceMapper::toDomain)
                .toList();
        return new FinancingRequest(entity.getId(), entity.getClient().getId(), entity.getAmount(),
                entity.getDeficitInDays(), entity.getStatus(), entity.getPurpose(), entity.getOrigin(),
                entity.getStaminaScore(), entity.getForecastDti(), entity.getIncomeStability(),
                entity.getDefaultProb12mo(), entity.getVerdict(), entity.getRiskTier(), entity.getCreatedAt(),
                proposals);
    }

    /** Project one proposal row to the domain {@link FinancingProposal}. */
    public static FinancingProposal toDomain(FinancingProposalJpaEntity entity) {
        return new FinancingProposal(entity.getId(), entity.getBankName(), entity.getStatus(),
                entity.getRate(), entity.getTermMonths(), entity.getAmount(), entity.getRepliedAt(),
                entity.getDisbursedAt(), entity.getFirstPaymentDate());
    }
}
