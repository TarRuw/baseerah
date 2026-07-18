package com.baseerah.application.infrastructure.security;

import com.baseerah.domain.auth.Role;
import java.util.UUID;

/**
 * The authenticated principal carried in the Spring Security context for the life of a request
 * (Phase 9, Step 9.2). Reconstructed purely from the JWT claims by the {@code JwtAuthFilter} — it never
 * reloads the {@code AppUserJpaEntity} — so it is immutable and cheap.
 *
 * @param userId   the {@code app_users.id} (JWT {@code sub})
 * @param mobile   the login mobile (E.164), for auditing/echo
 * @param role     the caller's {@link Role}, drives {@code ROLE_*} authority
 * @param clientId the linked {@code clients.id} for a {@link Role#CONSUMER}; {@code null} for a bank officer.
 *                 Step 9.3 uses this to enforce per-client ownership.
 */
public record AuthUser(UUID userId, String mobile, Role role, UUID clientId) {

    /** The Spring Security authority string for this principal, e.g. {@code ROLE_CONSUMER}. */
    public String authority() {
        return "ROLE_" + role.name();
    }
}
