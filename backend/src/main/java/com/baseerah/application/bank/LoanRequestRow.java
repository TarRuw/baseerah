package com.baseerah.application.bank;

import com.baseerah.domain.financing.FinancingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the Bank Portal <strong>underwrite-stage</strong> queue (Phase 12 / Step 12.3): a
 * consumer-originated loan request awaiting the operator's risk report, with just enough applicant context to
 * decide whether to underwrite it — the applicant's display label + avatar initials, the requested amount, and
 * the stated purpose. An application-layer read model projected from a {@code financing_requests} row; the web
 * mapper projects it to the wire DTO so the JPA entity never crosses the boundary (Global Rules).
 *
 * <p>Replaces the retired FR-08 {@code ApplicantView}/{@code ApplicantDto} queue row: there are no more
 * invented external applicants, so every row is a real client's request (a live {@code clientId} sits behind
 * {@code requestId}).
 *
 * @param requestId      the loan request to underwrite
 * @param applicantLabel the client's display label
 * @param initials       up-to-two-letter avatar initials from the label
 * @param amount         the requested SAR financing amount
 * @param purpose        the stated loan purpose (may be {@code null})
 * @param status         the request lifecycle status (OPEN in the queue; DECLINED after a bank decline)
 * @param createdAt      when the request was raised
 */
public record LoanRequestRow(
        UUID requestId,
        String applicantLabel,
        String initials,
        BigDecimal amount,
        String purpose,
        FinancingStatus status,
        Instant createdAt) {
}
