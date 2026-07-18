package com.baseerah.domain.financing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One bank's proposal within a financing request (the RFP fan-out). A pure domain value projected from the
 * {@code financing_proposals} row — the JPA entity never crosses the web boundary. While {@link #status()}
 * is {@link ProposalStatus#PENDING} the {@code rate}/{@code termMonths}/{@code repliedAt} are {@code null};
 * they are filled when the bank operator replies. The <em>impact</em> of a replied proposal (instalment,
 * DTI, projected score) is not carried here — the application service computes it on demand via the loan
 * engine and pairs it alongside this value.
 *
 * @param id         the proposal id
 * @param bankName   the targeted bank (a free-text {@code accounts.bank_name}; no bank entity exists)
 * @param status     PENDING / REPLIED / DECLINED
 * @param rate       the bank's nominal annual profit rate as a percentage, or {@code null} until replied
 * @param termMonths the bank's repayment term in months, or {@code null} until replied
 * @param amount           the offered SAR amount (defaults to the request's shortfall amount)
 * @param repliedAt        when the bank replied, or {@code null} while pending/declined
 * @param disbursedAt      when the bank disbursed the facility, or {@code null} until then
 * @param firstPaymentDate the first repayment due date once disbursed, or {@code null} until then
 */
public record FinancingProposal(
        UUID id,
        String bankName,
        ProposalStatus status,
        BigDecimal rate,
        Integer termMonths,
        BigDecimal amount,
        Instant repliedAt,
        Instant disbursedAt,
        LocalDate firstPaymentDate) {
}
