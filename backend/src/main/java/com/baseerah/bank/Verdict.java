package com.baseerah.bank;

/**
 * Underwriting verdict for a loan applicant (FR-08, DESIGN.md §5.5). Fixed by the §5.5 thresholds over the
 * predictive report's stamina score and forecast DTI:
 * <ul>
 *   <li>{@link #OK} — {@code stamina >= 70 AND DTI <= 34%} (strong endurance, comfortable leverage);</li>
 *   <li>{@link #BAD} — {@code stamina <= 48 OR DTI >= 71%} (fragile endurance or over-leveraged);</li>
 *   <li>{@link #WARN} — anything in between (mixed signals).</li>
 * </ul>
 *
 * <p>Persisted as text in {@code loan_applications.verdict}, constrained by the migration's
 * {@code chk_loan_applications_verdict} CHECK; the column is nullable so a queued-but-un-underwritten
 * applicant has no verdict yet.
 */
public enum Verdict {

    /** Strong applicant — {@code stamina >= 70 AND DTI <= 34%}. */
    OK,

    /** Mixed signals — neither clearly strong nor clearly failing. */
    WARN,

    /** Weak applicant — {@code stamina <= 48 OR DTI >= 71%}. */
    BAD
}
