package com.baseerah.domain.auth;

/**
 * The two authenticated audiences of Baseerah (DESIGN.md §12). Pure vocabulary — a domain enum with no
 * framework ties. Persisted as a string on {@code app_users.role} (by the {@code AppUserJpaEntity} mapping
 * in the application layer) and the basis for role gating in Step 9.3.
 */
public enum Role {

    /** An end-user tied to a seeded persona (a {@code clients} row); sees the consumer app. */
    CONSUMER,

    /** A bank officer with no persona link; sees the Bank Portal. */
    BANK
}
