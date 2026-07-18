package com.baseerah.application.infrastructure.persistence.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AppUserJpaEntity}. Sign-in and idempotent seeding both key off the
 * mobile number (the login handle).
 */
public interface AppUserRepository extends JpaRepository<AppUserJpaEntity, UUID> {

    Optional<AppUserJpaEntity> findByMobile(String mobile);

    boolean existsByMobile(String mobile);
}
