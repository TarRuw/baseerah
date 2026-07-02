package com.baseerah.client;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Client}. Lookups are by the stable {@code external_id}. */
public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);
}
