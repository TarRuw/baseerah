package com.baseerah.shared;

/**
 * Thrown when an authenticated caller is refused access to a resource they do not own — chiefly a consumer
 * reaching for another client's {@code /api/v1/clients/{id}/...} data (Phase 9, Step 9.3 ownership).
 *
 * <p>{@link GlobalExceptionHandler} maps this to HTTP 403 with code {@code FORBIDDEN}, mirroring the
 * {@link NotFoundException}/{@link ConflictException} pattern and matching the envelope the Spring Security
 * {@code AccessDeniedHandler} already emits for role-gating failures. The message is kept generic: an
 * ownership failure must not echo the other client's id or confirm its existence (no 404-style leak).
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
