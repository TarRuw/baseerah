package com.baseerah.domain.loan;

/**
 * Affordability verdict for a simulated loan (DESIGN.md §5.3): the monthly instalment measured against the
 * client's monthly surplus. The pure {@link LoanCalculator} emits this typed decision; the web layer
 * ({@code api/loan/LoanWebMapper}) resolves it to localized text and its DESIGN §8 band colour, so neither
 * locale nor palette leaks into the domain.
 */
public enum LoanVerdict {

    /** Instalment sits comfortably within the surplus (≤ 50%). */
    COMFORTABLE,

    /** Instalment is affordable but strains liquidity (≤ 85% of surplus). */
    STRAINS,

    /** Instalment exceeds the affordable share of surplus (or the surplus is non-positive). */
    NOT_AFFORDABLE
}
