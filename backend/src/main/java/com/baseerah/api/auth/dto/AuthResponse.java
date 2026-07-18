package com.baseerah.api.auth.dto;

/**
 * Success payload for {@code POST /api/v1/auth/otp/verify}: the bearer token to send on every subsequent
 * request, plus the caller's identity so the client need not immediately call {@code /me}.
 *
 * @param token the signed HS256 JWT (send as {@code Authorization: Bearer <token>})
 * @param user  the authenticated identity
 */
public record AuthResponse(String token, MeDto user) {
}
