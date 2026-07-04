package com.baseerah.bank;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the singleton {@link RiskPolicy}. The migration guarantees exactly one row, so
 * {@link #loadPolicy()} is the canonical "read the live policy" accessor used by the Step 6.2 risk-policy
 * endpoints and Step 6.4 Settings screen.
 */
public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, UUID> {

    /**
     * The single bank-wide risk policy. The {@code risk_policy} table is enforced as a singleton in the
     * schema (see {@code 006-bank.sql}), so this never returns more than one row.
     *
     * @return the live policy
     * @throws IllegalStateException if no policy row exists (the migration failed to seed it)
     */
    default RiskPolicy loadPolicy() {
        return findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No risk_policy row — the 006-bank migration must seed exactly one."));
    }
}
