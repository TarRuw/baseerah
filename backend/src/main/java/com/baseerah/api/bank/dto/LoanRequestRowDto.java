package com.baseerah.api.bank.dto;

import java.math.BigDecimal;

/**
 * Wire view of one Bank Portal <strong>underwrite-stage</strong> queue row (Phase 12 / Step 12.3): a
 * consumer loan request awaiting a risk report, with the applicant context the operator needs to pick it up.
 * Projected from the application {@code LoanRequestRow} by {@code BankWebMapper}; no JPA entity crosses the
 * boundary. Replaces the retired FR-08 {@code ApplicantDto}.
 *
 * @param requestId      the loan request to underwrite (UUID as string)
 * @param applicantLabel the client's display label
 * @param initials       up-to-two-letter avatar initials
 * @param amount         the requested SAR financing amount
 * @param purpose        the stated loan purpose ({@code null} when none was captured)
 * @param status         the request lifecycle status (OPEN in the queue; DECLINED after a decline)
 * @param createdAt      ISO-8601 instant the request was raised
 */
public record LoanRequestRowDto(
        String requestId,
        String applicantLabel,
        String initials,
        BigDecimal amount,
        String purpose,
        String status,
        String createdAt) {
}
