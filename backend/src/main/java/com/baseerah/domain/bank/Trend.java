package com.baseerah.domain.bank;

/** Health trend of a monitored facility relative to the portfolio mean — the table's ↑ / → / ↓ arrow (DESIGN §7.6). */
public enum Trend {

    /** Health above the portfolio mean by more than the deadband. */
    UP,

    /** Health within the deadband of the portfolio mean. */
    FLAT,

    /** Health below the portfolio mean by more than the deadband. */
    DOWN
}
