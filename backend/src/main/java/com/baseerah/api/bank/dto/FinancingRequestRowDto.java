package com.baseerah.api.bank.dto;

import java.math.BigDecimal;

/**
 * Wire view of one Bank Portal financing-inbox row: a consumer proposal the operator can price, with the
 * applicant context and risk hint ({@code clientScore}). Projected from the application {@code
 * FinancingInboxRow} by {@code BankFinancingWebMapper}; no JPA entity crosses the boundary.
 *
 * @param proposalId    the proposal to reply to (UUID as string)
 * @param requestId     the parent request (UUID as string)
 * @param bankName      the bank the proposal was addressed to
 * @param applicantLabel the client's display label
 * @param amount        the requested SAR amount to finance
 * @param clientScore   the client's current stress score (0–100) — a pricing risk hint
 * @param verdict       the parent request's underwriting verdict (OK/WARN/BAD), or {@code null} if not underwritten
 * @param staminaScore  the parent request's underwriting stamina (0–100), or {@code null} if not underwritten
 * @param status        PENDING / REPLIED / DECLINED
 * @param rate          the replied rate (%), or {@code null} while pending
 * @param termMonths    the replied term in months, or {@code null} while pending
 * @param createdAt     ISO-8601 instant the request was raised
 */
public record FinancingRequestRowDto(
        String proposalId,
        String requestId,
        String bankName,
        String applicantLabel,
        BigDecimal amount,
        int clientScore,
        String verdict,
        Integer staminaScore,
        String status,
        BigDecimal rate,
        Integer termMonths,
        String createdAt) {
}
