package com.baseerah.api.auth;

import com.baseerah.api.auth.dto.AuthResponse;
import com.baseerah.api.auth.dto.MeDto;
import com.baseerah.api.auth.dto.OtpRequest;
import com.baseerah.api.auth.dto.OtpVerifyRequest;
import com.baseerah.application.auth.AuthService;
import com.baseerah.application.infrastructure.security.AuthUser;
import com.baseerah.shared.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phone + OTP authentication endpoints (Step 9.2). Thin: delegates to {@link AuthService} and wraps
 * results in the shared {@link ApiResponse} envelope.
 *
 * <p>{@code /otp/request} and {@code /otp/verify} are public (a caller has no token yet); {@code /me}
 * requires a valid bearer token (enforced by the {@code SecurityConfig} chain).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** {@code POST /otp/request} — "send" a mock OTP. Always succeeds generically (no user enumeration). */
    @PostMapping("/otp/request")
    public ApiResponse<Void> requestOtp(@Valid @RequestBody OtpRequest request) {
        authService.requestOtp(request.mobile());
        return ApiResponse.ok(null);
    }

    /** {@code POST /otp/verify} — exchange a mobile + code for a JWT; {@code 401} on a bad code/mobile. */
    @PostMapping("/otp/verify")
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ApiResponse.ok(authService.verifyOtp(request.mobile(), request.otp()));
    }

    /** {@code GET /me} — the current caller's identity; {@code 401} without a valid token. */
    @GetMapping("/me")
    public ApiResponse<MeDto> me(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(authService.me(principal));
    }
}
