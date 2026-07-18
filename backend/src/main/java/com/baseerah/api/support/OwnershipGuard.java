package com.baseerah.api.support;

import com.baseerah.application.infrastructure.security.AuthUser;
import com.baseerah.domain.auth.Role;
import com.baseerah.shared.ForbiddenException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Enforces per-client ownership on the consumer API (Phase 9, Step 9.3): a {@link Role#CONSUMER} may act
 * only on <em>their own</em> {@code clients} row. Every {@code /api/v1/clients/{id}/...} controller method
 * calls {@link #assertOwns(String)} with the raw path {@code id} before doing any work; a mismatch (or a
 * malformed id, or a non-consumer principal) throws {@link ForbiddenException} → the shared {@code 403}
 * envelope. It is an api-layer authorization helper (one home, imported by every client-scoped controller).
 *
 * <p>Role gating (a consumer can reach {@code /clients/**} at all, a bank officer cannot) is handled by the
 * {@code SecurityConfig} authorization rules; this guard is the finer-grained, per-row check that role
 * gating cannot express. It reads the authenticated {@link AuthUser} straight from the security context, so
 * controllers stay thin and need not thread the principal through their signatures.
 *
 * <p><strong>No 404 leak.</strong> On any failure it returns a generic 403 that never echoes the path id or
 * distinguishes "not yours" from "does not exist" — so a consumer cannot probe the existence of other
 * clients by watching status codes.
 */
@Component("ownership")
public class OwnershipGuard {

    /**
     * Assert that the current consumer principal owns the client identified by the raw path {@code id}.
     *
     * @param id the {@code {id}} path variable (a client UUID as a string) from a consumer endpoint
     * @throws ForbiddenException if there is no consumer principal, the id is malformed, or it does not
     *     equal the caller's linked {@code clientId}
     */
    public void assertOwns(String id) {
        UUID callerClientId = currentConsumerClientId();
        UUID target;
        try {
            target = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            // A malformed id cannot be the caller's client; treat as a denial, not a 400/404 (no leak).
            throw forbidden();
        }
        if (!target.equals(callerClientId)) {
            throw forbidden();
        }
    }

    /**
     * Method-security friendly form: {@code true} iff the {@code authentication}'s consumer principal owns
     * {@code id}. Provided so the same rule can be expressed as
     * {@code @PreAuthorize("@ownership.owns(#id, authentication)")} if a controller prefers annotations;
     * the controllers in this codebase call {@link #assertOwns(String)} directly.
     */
    public boolean owns(String id, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return false;
        }
        if (user.role() != Role.CONSUMER || user.clientId() == null) {
            return false;
        }
        try {
            return user.clientId().equals(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** The linked {@code clientId} of the current consumer principal, or a 403 if there is not one. */
    private static UUID currentConsumerClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)
                || user.role() != Role.CONSUMER || user.clientId() == null) {
            throw forbidden();
        }
        return user.clientId();
    }

    private static ForbiddenException forbidden() {
        return new ForbiddenException("You do not have permission to access this resource.");
    }
}
