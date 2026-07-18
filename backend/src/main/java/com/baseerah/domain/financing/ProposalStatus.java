package com.baseerah.domain.financing;

/**
 * Lifecycle of a single bank proposal within a financing request. Persisted as text in
 * {@code financing_proposals.status} (CHECK {@code chk_financing_proposals_status}). Framework-free domain
 * vocabulary — the persistence entity maps it to/from its stored {@code name()}.
 */
public enum ProposalStatus {

    /** Sent to the bank, awaiting a reply — {@code rate}/{@code term} are still null. */
    PENDING,

    /** The bank operator replied with a profit rate and term. */
    REPLIED,

    /** The consumer accepted this offer's terms; it now awaits the bank's disbursement. */
    ACCEPTED,

    /** The bank disbursed the facility — it is now active with a repayment schedule. */
    DISBURSED,

    /** The bank declined — at the offer stage, or at the final disbursement check. */
    DECLINED
}
