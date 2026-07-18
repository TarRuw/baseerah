package com.baseerah.domain.rescue;

/**
 * The two Sharia-aware bridge options Smart Rescue offers when a cash-flow deficit is predicted
 * (FR-07, DESIGN.md §5.4). Persisted as text in {@code rescue_events.option_chosen} (matching the
 * {@code chk_rescue_option_chosen} CHECK constraint in migration {@code 004-rescue-events.sql}).
 *
 * <p>Domain vocabulary — framework-free (step 10.6). The persistence entity maps it to its stored
 * {@code name()} and the web DTO echoes it on the wire; neither changes the enum.
 */
public enum RescueOptionType {

    /** Pre-approved Sharia-compliant micro-finance: a financed amount repaid over a term, at a markup. */
    MURABAHA,

    /** Liquidate safe investment-fund assets to cover the shortfall — no financing cost, no repayment term. */
    LIQUIDATE
}
