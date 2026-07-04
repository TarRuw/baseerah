package com.baseerah.bank;

/**
 * The human lending decision a banker records against an underwritten applicant (FR-08, DESIGN.md §5.5).
 * Distinct from the model's {@link Verdict}: the verdict is the system's predictive recommendation, while
 * the decision is the acted-upon outcome the Step 6.2 decision endpoint writes.
 *
 * <p>Persisted as text in {@code loan_applications.decision}, constrained by the migration's
 * {@code chk_loan_applications_decision} CHECK; nullable until a decision is made.
 */
public enum Decision {

    /** The bank approves the requested financing. */
    APPROVE,

    /** The bank declines the requested financing. */
    DECLINE
}
