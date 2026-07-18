package com.baseerah.domain.bank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The request half of a loan applicant (FR-08, DESIGN.md §5.5) — the applicant identity and requested
 * financing the {@link UnderwritingRule} needs to echo into its {@link UnderwritingReport}. A pure domain
 * value the application shell builds from the persisted application row before invoking the rule, so the
 * rule never sees a JPA entity.
 *
 * @param applicationId the application's id
 * @param applicantName applicant display name
 * @param initials      applicant initials (avatar text)
 * @param purpose       stated loan purpose
 * @param amount        requested financing amount (SAR)
 * @param clientRef     linked seeded client, or {@code null} for a synthetic applicant
 */
public record ApplicantRequest(
        UUID applicationId,
        String applicantName,
        String initials,
        String purpose,
        BigDecimal amount,
        UUID clientRef) {
}
