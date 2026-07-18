package com.baseerah.application.auth;

import com.baseerah.api.auth.dto.AuthResponse;
import com.baseerah.api.auth.dto.MeDto;
import com.baseerah.application.infrastructure.persistence.auth.AppUserJpaEntity;
import com.baseerah.application.infrastructure.persistence.auth.AppUserPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.auth.AppUserRepository;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.infrastructure.persistence.client.ClientRepository;
import com.baseerah.application.infrastructure.security.AuthUser;
import com.baseerah.application.infrastructure.security.JwtService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the phone + OTP login flow (Step 9.2): it bridges the {@link OtpService} (mock code check)
 * and {@link JwtService} (token minting), and projects the {@link AppUserJpaEntity} to the
 * {@link MeDto}/{@link AuthResponse} DTOs (via {@link AppUserPersistenceMapper}) so the entity never leaves
 * the service layer (Global Rules). All business logic lives here; the controller is thin. This mirrors the
 * client/account/transaction anemic slices, which likewise map their entity to a web DTO in the service.
 */
@Service
public class AuthService {

    private final OtpService otpService;
    private final JwtService jwtService;
    private final AppUserRepository users;
    private final ClientRepository clients;
    private final AppUserPersistenceMapper mapper;

    public AuthService(OtpService otpService, JwtService jwtService, AppUserRepository users,
            ClientRepository clients, AppUserPersistenceMapper mapper) {
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.users = users;
        this.clients = clients;
        this.mapper = mapper;
    }

    /** Step 1 of login: "send" an OTP (mock). Never reveals whether the mobile is known. */
    public void requestOtp(String mobile) {
        otpService.requestOtp(mobile);
    }

    /**
     * Step 2 of login: verify the code and, on success, mint a JWT and return it with the caller's identity.
     *
     * @throws InvalidOtpException on a wrong code or unknown mobile (a single generic failure).
     */
    @Transactional(readOnly = true)
    public AuthResponse verifyOtp(String mobile, String otp) {
        AppUserJpaEntity user = otpService.verifyOtp(mobile, otp)
                .orElseThrow(() -> new InvalidOtpException("Invalid verification code."));
        String token = jwtService.issue(mapper.toPrincipal(user));
        return new AuthResponse(token, toMeDto(user));
    }

    /** Resolve the current caller (from the security context) to a fresh identity view. */
    @Transactional(readOnly = true)
    public MeDto me(AuthUser principal) {
        AppUserJpaEntity user = users.findById(principal.userId())
                .orElseThrow(() -> new InvalidOtpException("Unknown user."));
        return toMeDto(user);
    }

    private MeDto toMeDto(AppUserJpaEntity user) {
        UUID clientId = user.getClientId();
        String externalId = clientId == null ? null
                : clients.findById(clientId).map(ClientJpaEntity::getExternalId).orElse(null);
        return mapper.toMeDto(user, externalId);
    }
}
