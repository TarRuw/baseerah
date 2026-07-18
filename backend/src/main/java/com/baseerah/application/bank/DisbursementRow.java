package com.baseerah.application.bank;

import com.baseerah.domain.financing.ProposalStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A row of the Bank Portal disbursements queue: an offer the consumer accepted, awaiting the bank's funding
 * decision. Carries the applicant context, the accepted terms, and a final-check affordability signal
 * ({@code installment} + {@code affordabilityVerdict}, from the loan engine) plus the client's stress score —
 * enough for the operator to decide whether to disburse or decline at this final stage.
 *
 * @param proposalId          the accepted proposal to fund
 * @param requestId           the parent request
 * @param bankName            the bank funding it
 * @param applicantLabel      the client's display label
 * @param amount              the facility amount (SAR)
 * @param rate                the accepted profit rate (%)
 * @param termMonths          the accepted term in months
 * @param clientScore         the client's current stress score (0–100)
 * @param installment         the monthly instalment (SAR) the client will owe
 * @param affordabilityVerdict the loan-engine affordability verdict (COMFORTABLE / STRAINS / NOT_AFFORDABLE)
 * @param status              PENDING/REPLIED/ACCEPTED/DISBURSED/DECLINED (ACCEPTED in the queue; the post-
 *                            action state after disburse/decline)
 */
public record DisbursementRow(
        UUID proposalId,
        UUID requestId,
        String bankName,
        String applicantLabel,
        BigDecimal amount,
        BigDecimal rate,
        int termMonths,
        int clientScore,
        BigDecimal installment,
        String affordabilityVerdict,
        ProposalStatus status) {
}
