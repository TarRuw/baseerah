package com.baseerah.application.infrastructure.persistence.auth;

import com.baseerah.api.auth.dto.MeDto;
import com.baseerah.application.infrastructure.security.AuthUser;
import org.springframework.stereotype.Component;

/**
 * Projects the {@link AppUserJpaEntity} to the small views the auth use case hands out, so the entity never
 * crosses the controller boundary (Global Rules): the {@link AuthUser} security principal (the token
 * subject) and the {@link MeDto} identity projection (the {@code /me} shape, mirrored in {@code AuthResponse}).
 * The persona's stable {@code external_id} is not a column on {@code app_users}, so the caller resolves it
 * (via the client repository) and passes it in.
 *
 * <p>{@code app_user} is one of the anemic CRUD entities (ORCHESTRATION §Phase 10 decision) — it has no
 * domain twin, so the projection targets are the security principal and the web DTO directly, exactly as the
 * client/account/transaction slices map their entities to web DTOs.
 */
@Component
public class AppUserPersistenceMapper {

    /** The authenticated principal minted into a JWT — the token-subject view of a user. */
    public AuthUser toPrincipal(AppUserJpaEntity user) {
        return new AuthUser(user.getId(), user.getMobile(), user.getRole(), user.getClientId());
    }

    /**
     * The full identity projection returned by {@code /auth/me} and echoed on login.
     *
     * @param clientExternalId the linked persona's {@code external_id}, or {@code null} for a bank officer
     */
    public MeDto toMeDto(AppUserJpaEntity user, String clientExternalId) {
        var clientId = user.getClientId();
        return new MeDto(
                user.getId().toString(),
                user.getDisplayName(),
                user.getDisplayNameAr(),
                user.getRole().name(),
                clientId == null ? null : clientId.toString(),
                clientExternalId);
    }
}
