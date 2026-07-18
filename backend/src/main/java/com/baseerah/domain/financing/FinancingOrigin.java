package com.baseerah.domain.financing;

/**
 * How a loan/financing request was originated (Phase 12 unified pipeline). Persisted as text in
 * {@code financing_requests.origin} (CHECK {@code chk_financing_requests_origin}). Framework-free domain
 * vocabulary — the persistence entity maps it to/from its stored {@code name()}.
 */
public enum FinancingOrigin {

    /** Raised from Smart Rescue against a predicted deficit — every pre-Phase-12 request is this. */
    RESCUE,

    /** Raised directly by the consumer as an "apply for financing" request (Step 12.7 entry point). */
    DIRECT
}
