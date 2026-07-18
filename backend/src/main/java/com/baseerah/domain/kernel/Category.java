package com.baseerah.domain.kernel;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Application-level, typed view of a transaction category. The category set is data-driven (it arrives
 * from the SAMA Open-Banking feed), so this enum is intentionally <em>not</em> the source of truth: the
 * {@code Transaction} entity persists the raw feed string verbatim, and {@link #resolve(String)} maps it
 * to a known constant for scoring/analytics — falling back to {@link #OTHER} for any value we have not
 * modelled yet, rather than throwing.
 *
 * <p>Domain vocabulary (part of the kernel): the rule-bearing algorithms read it off each
 * {@code LedgerEntry}. The known set below mirrors every {@code category} observed across
 * {@code data-mocks/}; add a constant here (not a DB migration) when a new category needs first-class
 * treatment.
 */
public enum Category {

    SALARY,
    INFLOW_BUSINESS,
    INVESTMENT_INFLOW,
    GOVERNMENT_ALLOWANCE,
    GROCERIES,
    RESTAURANTS_DINING,
    RESTAURANTS_LUXURY,
    TRANSPORTATION,
    TRAVEL_FLIGHTS,
    TRAVEL_HOTELS,
    SHOPPING,
    SHOPPING_LUXURY,
    TELECOM,
    UTILITIES,
    HEALTHCARE,
    HEALTH_FITNESS,
    EDUCATION_BOOKS,
    ENTERTAINMENT_SUBSCRIPTIONS,
    SOFTWARE_SUBSCRIPTIONS,
    BUSINESS_EXPENSES,

    /**
     * Sentinel for any category not modelled above — resolution-time only for the bank feed (never
     * persisted on a {@code Transaction}). <strong>Exception (Phase 11, locked decision #3):</strong> a
     * user-declared periodic expense <em>may</em> be stored under {@code OTHER} (e.g. family support إعالة),
     * so it is offered by {@link #declarableExpenseCategories()} and accepted by {@link
     * #isDeclarableExpense(String)} for the {@code declared_expenses} table only.
     */
    OTHER;

    private static final Map<String, Category> BY_NAME = Stream.of(values())
            .filter(c -> c != OTHER)
            .collect(Collectors.toMap(c -> c.name().toUpperCase(Locale.ROOT), c -> c));

    /**
     * The income-direction categories — the closed complement of the sixteen spending categories. Direction
     * normally lives on a {@code LedgerEntry}; a user-declared expense has none, so Phase 11 needs the
     * discriminator on the category itself (see {@link #isExpense()}). Kept as the small, closed set so a
     * newly-added spending constant is treated as an expense by default (visible in the picker) rather than
     * silently excluded.
     */
    private static final Set<Category> INCOME =
            EnumSet.of(SALARY, INFLOW_BUSINESS, INVESTMENT_INFLOW, GOVERNMENT_ALLOWANCE);

    /**
     * Whether this is a spending category a user may declare as a periodic expense (Phase 11, Step 11.2).
     * The four {@link #INCOME} categories are {@code false}; the sixteen spending categories are {@code true}.
     *
     * <p>{@link #OTHER} is {@code false} here — it is a resolution-time sentinel, not a spending category.
     * It is nonetheless a legal <em>stored</em> declared-expense category (locked decision #3), but that
     * legality is granted <strong>explicitly</strong> at the picker vocabulary ({@link
     * #declarableExpenseCategories()}) and the write path, never by this predicate. Keeping {@code OTHER}
     * out of {@code isExpense()} stops the sentinel from leaking into any expense-only computation by
     * accident.
     */
    public boolean isExpense() {
        return this != OTHER && !INCOME.contains(this);
    }

    /**
     * The declarable-expense picker vocabulary (Phase 11, Step 11.2): every {@link #isExpense()} category,
     * plus {@link #OTHER} appended <em>deliberately</em> — the picker offers it and the declared-expense
     * write path stores it (locked decision #3) even though {@code OTHER} is not itself an expense category.
     * Income categories are absent. Ordered by enum declaration for a stable API; keys, never localized
     * strings — the Flutter ARB owns the labels.
     */
    public static List<Category> declarableExpenseCategories() {
        return Stream.of(values()).filter(c -> c.isExpense() || c == OTHER).toList();
    }

    /**
     * Whether a raw category string is a legal <em>stored</em> declared-expense category (Phase 11, Step
     * 11.2). Case/whitespace-tolerant like {@link #resolve(String)}, but stricter than it: a string that
     * merely <em>falls back</em> to {@link #OTHER} (an unknown category) is rejected unless it literally
     * names {@code OTHER}, and an income category is rejected too. So {@code UTILITIES} and {@code OTHER}
     * pass; {@code SALARY} and {@code NOPE} do not.
     */
    public static boolean isDeclarableExpense(String raw) {
        String key = raw == null ? "" : raw.strip();
        if (key.equalsIgnoreCase(OTHER.name())) {
            return true;
        }
        return resolve(key).isExpense();
    }

    /**
     * Maps a raw feed category to a known {@link Category}, or {@link #OTHER} when unrecognised.
     * Case-insensitive and whitespace-tolerant; {@code null}/blank resolves to {@link #OTHER}.
     */
    public static Category resolve(String raw) {
        if (raw == null) {
            return OTHER;
        }
        return BY_NAME.getOrDefault(raw.strip().toUpperCase(Locale.ROOT), OTHER);
    }

    /** Whether {@code raw} matches a modelled category (i.e. resolves to something other than OTHER). */
    public static boolean isKnown(String raw) {
        return resolve(raw) != OTHER;
    }

    /** The modelled category for {@code raw}, or empty when it would fall back to OTHER. */
    public static Optional<Category> tryResolve(String raw) {
        Category resolved = resolve(raw);
        return resolved == OTHER ? Optional.empty() : Optional.of(resolved);
    }
}
