package com.baseerah.application.infrastructure.persistence.expense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link DeclaredExpenseJpaEntity}. Declared expenses are client-scoped; the two
 * finders express the only two access patterns Step 11.1 needs.
 */
public interface DeclaredExpenseRepository extends JpaRepository<DeclaredExpenseJpaEntity, UUID> {

    /** All <em>active</em> declared expenses for a client — the list read (and Step 11.3's calculator feed). */
    List<DeclaredExpenseJpaEntity> findByClient_IdAndActiveTrue(UUID clientId);

    /**
     * One declared expense scoped to its owning client (active or not, so a repeat {@code DELETE} still
     * resolves the already-inactive row). Empty when the id is unknown <em>or</em> belongs to another client
     * — the service treats both as forbidden, never revealing which (no existence probing).
     */
    Optional<DeclaredExpenseJpaEntity> findByIdAndClient_Id(UUID id, UUID clientId);
}
