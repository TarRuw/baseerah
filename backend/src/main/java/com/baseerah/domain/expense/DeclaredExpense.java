package com.baseerah.domain.expense;

import com.baseerah.domain.kernel.Category;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A recurring outflow a user declared themselves — cash rent, family support (إعالة), private tuition — that
 * the SAMA Open-Banking feed never sees. The first <em>user-authored</em> financial value in the domain
 * (Phase 11 / GitLab backend#1).
 *
 * <p>Pure and framework-free, like {@code kernel/LedgerEntry}: this is the type Step 11.3's calculators
 * consume additively when they widen the client's obligation picture (raising {@code obligationShare},
 * lowering the stress score, worsening DTI). It carries only the facts a rule needs — the monthly
 * {@code amount}, its {@link Category}, the recurrence {@code dayOfMonth}, and whether it is still
 * {@code active}. Presentation-only fields ({@code currency}, {@code cadence}, timestamps) live on the JPA
 * row, not here: currency is SAR-only and cadence MONTHLY-only for now (ORCHESTRATION Phase 11), so a rule
 * never branches on them.
 *
 * @param id         the persistent identity of the declared expense
 * @param label      the user's own words for it (Arabic-first, e.g. {@code إيجار الشقة})
 * @param category   the resolved {@link Category} (may be {@link Category#OTHER} — a legal stored value for
 *                   declared expenses, unlike feed transactions)
 * @param amount     the recurring monthly amount in SAR (always positive)
 * @param dayOfMonth the day of the month the expense recurs (1..31)
 * @param active     {@code false} once soft-deleted; only active expenses feed the calculators
 */
public record DeclaredExpense(
        UUID id,
        String label,
        Category category,
        BigDecimal amount,
        int dayOfMonth,
        boolean active) {
}
