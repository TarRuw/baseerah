package com.baseerah.api.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Client-facing view of a bank account (DESIGN.md §6, {@code GET /api/v1/clients/{id}/accounts}).
 *
 * <p>Deliberately <strong>omits the raw account {@code external_id}</strong>: callers only ever see the
 * non-reversible {@code tokenizedAccountId} (SAMA tokenization, DESIGN.md §9). {@code latestBalance} is
 * a {@code numeric(14,2)} amount in {@code currency} — formatting to {@code SAR}/{@code ر.س} is the
 * Flutter layer's job (DESIGN.md §8), not this DTO's.
 *
 * @param id                 canonical UUID primary key
 * @param bankName           owning bank (deterministically assigned at seed time)
 * @param bankCode           the bank's stable code from the {@code banks} directory (e.g. {@code RAJHI}),
 *                           or {@code null} when {@code bankName} matches no directory row
 * @param logoSlug           key for the bank's bundled mark ({@code assets/banks/<logoSlug>.png}), or
 *                           {@code null} when the bank is not in the directory — the UI then falls back to
 *                           a monogram, so an unlisted bank costs a logo and never the accounts list
 * @param displayColor       theme colour for the account card (DESIGN.md §8 palette)
 * @param currency           ISO currency code
 * @param latestBalance      most recent closing balance
 * @param tokenizedAccountId non-reversible token standing in for the raw account id
 */
public record AccountDto(
        UUID id,
        String bankName,
        String bankCode,
        String logoSlug,
        String displayColor,
        String currency,
        BigDecimal latestBalance,
        String tokenizedAccountId) {
}
