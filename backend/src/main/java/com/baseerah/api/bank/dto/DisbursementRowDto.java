package com.baseerah.api.bank.dto;

import java.math.BigDecimal;

/**
 * Wire view of a Bank Portal disbursements-queue row: an accepted offer awaiting funding, with the applicant
 * context, accepted terms, and a final affordability signal ({@code installment} + {@code affordabilityVerdict})
 * so the operator can decide to disburse or decline. Projected from the application {@code DisbursementRow}.
 *
 * @param proposalId           the accepted proposal (UUID as string)
 * @param requestId            the parent request (UUID as string)
 * @param bankName             the bank funding it
 * @param applicantLabel       the client's display label
 * @param amount               the facility amount (SAR)
 * @param rate                 the accepted profit rate (%)
 * @param termMonths           the accepted term in months
 * @param clientScore          the client's current stress score (0–100)
 * @param installment          the monthly instalment (SAR)
 * @param affordabilityVerdict COMFORTABLE / STRAINS / NOT_AFFORDABLE
 * @param status               PENDING/REPLIED/ACCEPTED/DISBURSED/DECLINED
 */
public record DisbursementRowDto(
        String proposalId,
        String requestId,
        String bankName,
        String applicantLabel,
        BigDecimal amount,
        BigDecimal rate,
        int termMonths,
        int clientScore,
        BigDecimal installment,
        String affordabilityVerdict,
        String status) {
}
