package com.baseerah.bank.dto;

import com.baseerah.bank.Decision;
import com.baseerah.bank.LoanApplication;
import com.baseerah.bank.Verdict;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Wire view of one applicant in the underwriting queue (FR-08, DESIGN.md §7.5), served as a list item by
 * {@code GET /api/v1/bank/applicants} and as the decision endpoint's echo. Maps from a {@link LoanApplication}
 * so the entity never crosses the controller boundary (Global Rules).
 *
 * <p>The report-derived fields are <em>nullable</em>: a freshly-seeded synthetic applicant sits in the queue
 * un-underwritten, so {@code verdict}/{@code riskTier} stay {@code null} until a report is generated, letting
 * the Step 6.3 pipeline render an "unscored" state rather than a placeholder. {@code decision} is {@code null}
 * until a banker acts. The {@code verdict} doubles as the list's risk badge.
 *
 * @param id            the application id
 * @param applicantName applicant display name
 * @param initials      applicant initials (avatar text)
 * @param purpose       stated loan purpose
 * @param amount        requested financing amount (SAR)
 * @param verdict       the §5.5 risk badge, or {@code null} if not yet underwritten
 * @param riskTier      tier label (A/B/C) consistent with the verdict, or {@code null} if not yet underwritten
 * @param decision      the recorded human decision, or {@code null} if none yet
 */
public record ApplicantDto(
        UUID id,
        String applicantName,
        String initials,
        String purpose,
        BigDecimal amount,
        Verdict verdict,
        String riskTier,
        Decision decision) {

    /** Map a persisted {@link LoanApplication} to its queue wire view. */
    public static ApplicantDto from(LoanApplication app) {
        return new ApplicantDto(app.getId(), app.getApplicantName(), app.getInitials(), app.getPurpose(),
                app.getAmount(), app.getVerdict(), app.getRiskTier(), app.getDecision());
    }
}
