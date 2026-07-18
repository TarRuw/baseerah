package com.baseerah.application.bank;

import com.baseerah.domain.bank.Verdict;
import com.baseerah.domain.financing.ProposalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single row of the Bank Portal financing-requests inbox: one consumer proposal awaiting (or carrying) the
 * bank operator's reply, with just enough applicant context to price it — the targeted bank, the requested
 * amount, the applicant's display label, and a risk hint ({@code clientScore}, the consumer's current stress
 * score). An application-layer read model; the web mapper projects it to the wire DTO.
 *
 * <p><strong>Phase 12 (Step 12.3).</strong> Now that every request is underwritten before it is priced, the
 * pricing row also carries the parent request's underwriting outcome — {@code verdict} + {@code staminaScore}
 * — so the operator sees the applicant's creditworthiness while entering rate/term. Both are {@code null} when
 * the request has not been underwritten yet (read-only hints; they add no pricing logic).
 *
 * @param proposalId    the proposal to reply to
 * @param requestId     the parent financing request
 * @param bankName      the bank this proposal was addressed to (the operator replies on its behalf)
 * @param applicantLabel the client's display label
 * @param amount        the requested SAR amount to finance
 * @param clientScore   the client's current stress score (0–100) — a pricing risk hint
 * @param verdict       the parent request's underwriting verdict (OK/WARN/BAD), or {@code null} if not underwritten
 * @param staminaScore  the parent request's underwriting stamina (0–100), or {@code null} if not underwritten
 * @param status        PENDING / REPLIED / DECLINED
 * @param rate          the replied rate (%), or {@code null} while pending
 * @param termMonths    the replied term in months, or {@code null} while pending
 * @param createdAt     when the request was raised
 */
public record FinancingInboxRow(
        UUID proposalId,
        UUID requestId,
        String bankName,
        String applicantLabel,
        BigDecimal amount,
        int clientScore,
        Verdict verdict,
        Integer staminaScore,
        ProposalStatus status,
        BigDecimal rate,
        Integer termMonths,
        Instant createdAt) {
}
