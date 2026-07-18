package com.baseerah.application.infrastructure.persistence.bankdirectory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads the {@code banks} directory (changeset 013). Reference data — eight rows, read-only at runtime.
 */
public interface BankRepository extends JpaRepository<BankJpaEntity, UUID> {
}
