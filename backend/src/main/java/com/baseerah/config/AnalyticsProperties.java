package com.baseerah.config;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bound {@code baseerah.analytics.*} configuration: the day the scoring engines treat as "today".
 *
 * <p><strong>Why this exists.</strong> The engines score a trailing 90-day window and project forward from
 * the latest closing balance, but the demo telemetry is <em>frozen</em> — generated once by
 * {@code db-seed/generate_personas.py} with its newest transaction on that script's {@code ANCHOR_DATE}.
 * Left on the wall clock, the window slides forward every day while the data stays put, so the personas
 * drift a little further from the postures they were engineered to have, and eventually
 * ({@code ANCHOR_DATE + 90d}) the window stops overlapping the data at all and every score degenerates.
 *
 * <p>That is not theoretical: one day after the anchor, {@code client_001_family}'s stamina rose 99 → 100 —
 * enough to fail {@code CompliancePolicyTest}, which needs a linked applicant below 100 to screen.
 *
 * <p>So the engines anchor to {@link #asOfDate} instead, which defaults to the day the telemetry was
 * generated. The demo is then reproducible on any date, and the personas keep the zones they were tuned
 * for. {@code AnalyticsAsOfDateTest} fails if this drifts out of step with the generator.
 *
 * <p>This governs <em>analytics</em> only. Authentication and audit trails stay on the wall clock — a token
 * must expire in real time and a claim must be stamped with the moment it happened, regardless of which day
 * the ledger is scored against.
 *
 * @param asOfDate the day the scoring engines treat as "today". Override with {@code BASEERAH_AS_OF_DATE};
 *                 set it to {@code system} to follow the wall clock instead, which is what a deployment
 *                 with live (non-frozen) telemetry wants.
 */
@ConfigurationProperties(prefix = "baseerah.analytics")
public record AnalyticsProperties(@DefaultValue("2026-07-16") String asOfDate) {

    /** The literal that opts out of the fixed anchor and follows the wall clock. */
    public static final String SYSTEM = "system";

    /** Whether the engines should follow the wall clock rather than a fixed day. */
    public boolean followsSystemClock() {
        return asOfDate == null || asOfDate.isBlank() || SYSTEM.equalsIgnoreCase(asOfDate.strip());
    }

    /** The configured anchor day. Only valid when {@link #followsSystemClock()} is false. */
    public LocalDate anchor() {
        return LocalDate.parse(asOfDate.strip());
    }
}
