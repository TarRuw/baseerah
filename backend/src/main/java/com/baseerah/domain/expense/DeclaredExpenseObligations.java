package com.baseerah.domain.expense;

import java.math.BigDecimal;
import java.util.List;

/**
 * The single, shared rule for turning a client's declared periodic expenses into a monthly obligation total
 * (Phase 11 / GitLab backend#2). This is the <strong>parity anchor</strong> for Step 11.3: the stress score
 * (§5.1 liability) and the bank underwriting rule (§5.5 DTI) each read declared expenses <em>additively</em>
 * into their recurring-obligation figure, and they compute that figure with a byte-for-byte-duplicated
 * inference over the feed. Rather than duplicate the declared contribution a third and fourth time — and let
 * the two drift — both call {@link #monthlyTotal(List)}. Making the declared contribution a single method is
 * what turns "the two agree" from a comment into a structural invariant (enforced by
 * {@code UnderwritingStressParityTest}).
 *
 * <p>Pure and framework-free (domain layer): it takes domain {@link DeclaredExpense} records and returns a
 * {@link BigDecimal}, never touching Spring, JPA, or the clock.
 *
 * <p><strong>Monthly by construction.</strong> A declared expense's cadence is MONTHLY-only for now
 * (ORCHESTRATION Phase 11), so each active expense contributes its full {@code amount} to the monthly total —
 * no proration, no day-of-month reasoning (that is the forecast's concern, not the obligation total's).
 * Inactive (soft-deleted) expenses contribute nothing, so a deactivated expense has <em>no effect</em> on any
 * score or verdict even if it is passed in.
 */
public final class DeclaredExpenseObligations {

    private DeclaredExpenseObligations() {
    }

    /**
     * The total monthly obligation from a client's declared expenses: the sum of every <em>active</em>
     * expense's {@code amount}. Inactive expenses are skipped, so the caller may pass a mixed list and still
     * get the "active only" total (the read path {@code findByClient_IdAndActiveTrue} already filters, so in
     * production the list is active-only; the filter here makes {@code active = false → no effect} a
     * domain-level guarantee, not a repository detail).
     *
     * @param declared the client's declared expenses (may be empty or {@code null}); a {@code null} or empty
     *                 list yields {@link BigDecimal#ZERO}, so a feed-only client is bit-identical to today
     * @return the summed monthly amount of the active declared expenses, in SAR
     */
    public static BigDecimal monthlyTotal(List<DeclaredExpense> declared) {
        if (declared == null || declared.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (DeclaredExpense expense : declared) {
            if (expense != null && expense.active() && expense.amount() != null) {
                total = total.add(expense.amount());
            }
        }
        return total;
    }
}
