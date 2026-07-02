package com.baseerah.transaction;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Application-level, typed view of a transaction category. The category set is data-driven (it arrives
 * from the SAMA Open-Banking feed), so this enum is intentionally <em>not</em> the source of truth:
 * {@link Transaction#getCategory()} persists the raw feed string verbatim, and {@link #resolve(String)}
 * maps it to a known constant for scoring/analytics — falling back to {@link #OTHER} for any value we
 * have not modelled yet, rather than throwing.
 *
 * <p>The known set below mirrors every {@code category} observed across {@code data-mocks/}; add a
 * constant here (not a DB migration) when a new category needs first-class treatment.
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

    /** Sentinel for any category not modelled above. Never persisted — resolution-time only. */
    OTHER;

    private static final Map<String, Category> BY_NAME = Stream.of(values())
            .filter(c -> c != OTHER)
            .collect(Collectors.toMap(c -> c.name().toUpperCase(Locale.ROOT), c -> c));

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
