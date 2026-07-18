package com.baseerah.api.auth.dto;

/**
 * The current caller's identity, returned by {@code GET /api/v1/auth/me} and embedded in
 * {@link AuthResponse}. A projection of the {@code AppUserJpaEntity} produced by
 * {@code AppUserPersistenceMapper} — the entity never leaves the service layer (Global Rules).
 *
 * @param userId            the {@code app_users.id}
 * @param displayName       human name, English
 * @param displayNameAr     human name, Arabic
 * @param role              {@code CONSUMER} or {@code BANK}
 * @param clientId          the linked {@code clients.id} for a consumer; {@code null} for a bank officer
 * @param clientExternalId  the linked persona's stable {@code external_id} (e.g. {@code client_001_family});
 *                          {@code null} for a bank officer — lets the Flutter client pick the persona shell
 */
public record MeDto(
        String userId,
        String displayName,
        String displayNameAr,
        String role,
        String clientId,
        String clientExternalId) {
}
