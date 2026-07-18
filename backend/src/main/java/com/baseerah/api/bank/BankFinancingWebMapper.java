package com.baseerah.api.bank;

import com.baseerah.api.bank.dto.DisbursementRowDto;
import com.baseerah.api.bank.dto.FinancingRequestRowDto;
import com.baseerah.application.bank.DisbursementRow;
import com.baseerah.application.bank.FinancingInboxRow;

/**
 * Pure, static projection of the bank-side financing read model ({@link FinancingInboxRow}) to its wire DTO —
 * the financing analogue of {@link BankWebMapper}. Kept separate so the compliance-heavy {@code BankWebMapper}
 * stays focused on the underwriting/portfolio surface.
 */
public final class BankFinancingWebMapper {

    private BankFinancingWebMapper() {
    }

    /** Map an inbox row to its wire DTO (ids and instant to strings; status/verdict enums to name). */
    public static FinancingRequestRowDto toRowDto(FinancingInboxRow row) {
        return new FinancingRequestRowDto(
                row.proposalId().toString(),
                row.requestId().toString(),
                row.bankName(),
                row.applicantLabel(),
                row.amount(),
                row.clientScore(),
                row.verdict() == null ? null : row.verdict().name(),
                row.staminaScore(),
                row.status().name(),
                row.rate(),
                row.termMonths(),
                row.createdAt() == null ? null : row.createdAt().toString());
    }

    /** Map a disbursement-queue row to its wire DTO. */
    public static DisbursementRowDto toDisbursementDto(DisbursementRow row) {
        return new DisbursementRowDto(
                row.proposalId().toString(),
                row.requestId().toString(),
                row.bankName(),
                row.applicantLabel(),
                row.amount(),
                row.rate(),
                row.termMonths(),
                row.clientScore(),
                row.installment(),
                row.affordabilityVerdict(),
                row.status().name());
    }
}
