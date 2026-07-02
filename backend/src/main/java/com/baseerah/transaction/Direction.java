package com.baseerah.transaction;

/**
 * Credit/debit indicator for a {@link Transaction}, mirroring the SAMA Open-Banking
 * {@code credit_debit_indicator} field. Persisted as its {@code name()} via
 * {@code @Enumerated(EnumType.STRING)} to match the {@code text} column in {@code transactions}.
 */
public enum Direction {
    DEBIT,
    CREDIT
}
