package com.baseerah.domain.financing;

/**
 * Lifecycle of a consumer financing request (the RFP a client raises against a predicted deficit). Persisted
 * as text in {@code financing_requests.status} (CHECK {@code chk_financing_requests_status}). Framework-free
 * domain vocabulary — the persistence entity maps it to/from its stored {@code name()}.
 */
public enum FinancingStatus {

    /** The request is live: proposals are out to the targeted banks, none accepted yet. */
    OPEN,

    /** The client accepted one offer; it awaits the bank's disbursement. */
    ACCEPTED,

    /** The accepted offer was disbursed — the facility is active with a repayment schedule. */
    ACTIVE,

    /** The client abandoned the request (reserved; not yet raised by the flow). */
    CANCELLED,

    /** The bank rejected the request outright (Phase 12 unified pipeline — a per-request underwriting decline). */
    DECLINED
}
