package com.baseerah.application.infrastructure.persistence.financing;

import com.baseerah.domain.financing.ProposalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link FinancingProposalJpaEntity}. Backs both the consumer read (proposals are
 * loaded eagerly with their request) and the Bank Portal inbox, which lists proposals by lifecycle status.
 */
public interface FinancingProposalRepository extends JpaRepository<FinancingProposalJpaEntity, UUID> {

    /** All proposals in a given lifecycle state, oldest first — the Bank Portal inbox query. */
    List<FinancingProposalJpaEntity> findByStatusOrderByCreatedAtAsc(ProposalStatus status);
}
