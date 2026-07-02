package com.baseerah.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Account}. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByClientId(UUID clientId);

    Optional<Account> findByExternalId(String externalId);
}
