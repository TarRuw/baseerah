package com.baseerah.application.infrastructure.security;

import com.baseerah.config.AuthProperties;
import com.baseerah.domain.auth.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Issues and validates the HS256 bearer JWTs that back phone + OTP authentication (Step 9.2).
 *
 * <p>A token's {@code sub} is the {@code app_users.id}; it additionally carries {@code mobile},
 * {@code role}, and — for consumers — {@code clientId}, which is exactly the data {@link AuthUser}
 * needs, so the {@code JwtAuthFilter} authenticates without a database round-trip. The signing key and
 * TTL come from {@link AuthProperties} (env-overridable, dev-only defaults). The only clock use is the
 * {@code iat}/{@code exp} stamps and expiry check; the {@link Clock} is injectable so expiry is
 * deterministically unit-testable.
 */
@Service
public class JwtService {

    private static final String CLAIM_MOBILE = "mobile";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_CLIENT_ID = "clientId";

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(AuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Test seam: inject a fixed {@link Clock} to drive {@code iat}/{@code exp} deterministically. */
    JwtService(AuthProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.jwtTtl();
        this.clock = clock;
    }

    /** Mint a signed token for {@code user}, expiring {@code jwtTtl} from now. */
    public String issue(AuthUser user) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .subject(user.userId().toString())
                .claim(CLAIM_MOBILE, user.mobile())
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                // Pin HS256 explicitly: jjwt would otherwise auto-select HS384/HS512 for a >32-byte key.
                .signWith(key, Jwts.SIG.HS256);
        if (user.clientId() != null) {
            builder.claim(CLAIM_CLIENT_ID, user.clientId().toString());
        }
        return builder.compact();
    }

    /**
     * Validate a token's signature and expiry and reconstruct the {@link AuthUser} from its claims.
     *
     * @throws JwtException if the token is malformed, tampered, or expired (callers treat this as
     *     "unauthenticated"; never leak the reason to the client).
     */
    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        String mobile = claims.get(CLAIM_MOBILE, String.class);
        Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
        String clientId = claims.get(CLAIM_CLIENT_ID, String.class);
        return new AuthUser(userId, mobile, role, clientId == null ? null : UUID.fromString(clientId));
    }
}
