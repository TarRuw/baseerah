package com.baseerah.application.infrastructure.persistence.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AccountJpaEntity}. */
public interface AccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

    List<AccountJpaEntity> findByClientId(UUID clientId);

    /**
     * All accounts for a set of clients in one query — the batch form of {@link #findByClientId} that lets
     * the bank portfolio resolve every applicant's compliance tokens without an N+1 (Step 7.3). Backed by
     * {@code idx_accounts_client_id}.
     */
    List<AccountJpaEntity> findByClientIdIn(Collection<UUID> clientIds);

    Optional<AccountJpaEntity> findByExternalId(String externalId);
}
