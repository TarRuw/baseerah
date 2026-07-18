package com.baseerah.application.auth;

import com.baseerah.application.infrastructure.persistence.auth.AppUserJpaEntity;
import com.baseerah.application.infrastructure.persistence.auth.AppUserRepository;
import com.baseerah.config.AuthProperties;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * <strong>Mocked</strong> one-time-password provider for the local demo (Phase 9 intro, Step 9.2).
 *
 * <p>There is <em>no real SMS/OTP gateway</em>. "Sending" an OTP is a no-op log line, and verification
 * accepts a single fixed value ({@link AuthProperties#mockOtp()}, supplied from the environment via
 * {@code BASEERAH_AUTH_MOCK_OTP}; no in-code default). The mock is deliberately honest: {@link #verifyOtp}
 * still requires a real, provisioned user, so the fixed code does not by itself grant access.
 * <strong>Swap this class for a real provider adapter before production use;</strong> nothing else changes.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final AppUserRepository users;
    private final String mockOtp;

    public OtpService(AppUserRepository users, AuthProperties properties) {
        this.users = users;
        this.mockOtp = properties.mockOtp();
    }

    /**
     * "Dispatch" an OTP to {@code mobile}. A no-op mock that logs a masked dispatch line and always
     * returns quietly — whether or not the mobile belongs to a known user — so callers cannot probe for
     * valid accounts (no user enumeration). The gate on a real user lives solely in {@link #verifyOtp}.
     * The OTP value is never logged.
     */
    public void requestOtp(String mobile) {
        log.info("OTP dispatched to mobile ending in {}", maskedTail(mobile));
    }

    /**
     * Verify {@code otp} for {@code mobile}. Succeeds iff the code equals the configured mock OTP
     * <em>and</em> a seeded user owns that mobile.
     *
     * @return the resolved user on success, or {@link Optional#empty()} for a wrong code or an unknown
     *     mobile — the caller must not distinguish the two (a single generic failure).
     */
    public Optional<AppUserJpaEntity> verifyOtp(String mobile, String otp) {
        if (!mockOtp.equals(otp)) {
            return Optional.empty();
        }
        return users.findByMobile(mobile);
    }

    private static String maskedTail(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "****";
        }
        return "***" + mobile.substring(mobile.length() - 3);
    }
}
