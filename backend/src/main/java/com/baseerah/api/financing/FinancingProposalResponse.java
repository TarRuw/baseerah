package com.baseerah.api.financing;

import com.baseerah.api.loan.LoanSimulateResponse;
import java.math.BigDecimal;

/**
 * API view of one bank proposal within a financing request. While {@code status} is {@code PENDING} the
 * {@code rate}/{@code termMonths} and {@code impact} are {@code null} (the bank has not replied yet); once
 * {@code REPLIED} they carry the bank's offer and the reused loan-affordability {@link #impact} — instalment,
 * total, DTI (+ band colours), affordability verdict, and the projected stress score — so the consumer sees
 * exactly how each offer affects their situation.
 *
 * @param id         the proposal id (UUID as string)
 * @param bankName   the bank that was asked
 * @param status     PENDING / REPLIED / DECLINED
 * @param rate             the bank's nominal annual profit rate (%), or {@code null} until replied
 * @param termMonths       the bank's repayment term in months, or {@code null} until replied
 * @param amount           the offered SAR amount
 * @param firstPaymentDate the first repayment due date (ISO), set once the facility is disbursed, else null
 * @param impact           the loan-engine affordability impact, or {@code null} until replied
 */
public record FinancingProposalResponse(
        String id,
        String bankName,
        String status,
        BigDecimal rate,
        Integer termMonths,
        BigDecimal amount,
        String firstPaymentDate,
        LoanSimulateResponse impact) {
}
