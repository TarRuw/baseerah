package com.baseerah.domain.kernel;

/**
 * Credit/debit indicator for a ledger entry, mirroring the SAMA Open-Banking
 * {@code credit_debit_indicator} field. Domain vocabulary shared across the rule-bearing algorithms; the
 * {@code Transaction} JPA entity persists it as its {@code name()} via {@code @Enumerated(EnumType.STRING)}
 * to match the {@code text} column in {@code transactions}.
 */
public enum Direction {
    DEBIT,
    CREDIT
}
