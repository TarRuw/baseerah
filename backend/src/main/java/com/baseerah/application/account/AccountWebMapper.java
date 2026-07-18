package com.baseerah.application.account;

import com.baseerah.api.account.dto.AccountDto;
import com.baseerah.application.infrastructure.persistence.account.AccountJpaEntity;
import com.baseerah.application.infrastructure.persistence.bankdirectory.BankJpaEntity;

/**
 * Maps {@link AccountJpaEntity} entities to {@link AccountDto} projections. Pure and stateless.
 * Critically, it <strong>does not copy the raw {@code external_id}</strong> — only the
 * {@code tokenizedAccountId} is carried through, so the sensitive id can never leak to a caller
 * (SAMA §9). Reads scalar fields only; never touches the lazy {@code client}/{@code transactions}
 * associations.
 *
 * <p>Lives in the application layer (not api): it is the anemic-slice edge where a persistence entity
 * becomes a web DTO, so the api layer never depends on a JPA entity (Phase 10.11 dependency rule).
 */
public final class AccountWebMapper {

    private AccountWebMapper() {
    }

    /**
     * @param bank the directory row whose {@code name} matches {@code account.bankName}, or {@code null}
     *             when the bank is not listed — the code and logo slug are then {@code null} and the UI
     *             falls back to a monogram.
     */
    public static AccountDto toDto(AccountJpaEntity account, BankJpaEntity bank) {
        return new AccountDto(
                account.getId(),
                account.getBankName(),
                bank == null ? null : bank.getCode(),
                bank == null ? null : bank.getLogoSlug(),
                account.getDisplayColor(),
                account.getCurrency(),
                account.getLatestBalance(),
                account.getTokenizedAccountId());
    }
}
