package com.baseerah.account.dto;

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
 * @param displayColor       theme colour for the account card (DESIGN.md §8 palette)
 * @param currency           ISO currency code
 * @param latestBalance      most recent closing balance
 * @param tokenizedAccountId non-reversible token standing in for the raw account id
 */
public record AccountDto(
        UUID id,
        String bankName,
        String displayColor,
        String currency,
        BigDecimal latestBalance,
        String tokenizedAccountId) {
}
