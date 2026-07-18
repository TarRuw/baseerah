package com.baseerah.application.infrastructure.persistence.client;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link ClientJpaEntity}. Lookups are by the stable {@code external_id}. */
public interface ClientRepository extends JpaRepository<ClientJpaEntity, UUID> {

    Optional<ClientJpaEntity> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);
}
