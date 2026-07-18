package com.baseerah.domain.bank;

/**
 * Monitoring status badge for a portfolio facility: Healthy · Watch · At-risk, banded off the facility's
 * health (stamina) score so it always agrees with the shown figure (UI-06). §5.5-aligned cutoffs — see the
 * {@code BankService.statusFor(int)} mapping.
 */
public enum Status {

    /** Health ≥ 70 (the §5.5 OK stamina floor) — comfortably within policy. */
    HEALTHY,

    /** Health 49–69 — mixed signals, keep watching. */
    WATCH,

    /** Health ≤ 48 (the §5.5 BAD stamina ceiling) — fragile, flagged for action. */
    AT_RISK
}
