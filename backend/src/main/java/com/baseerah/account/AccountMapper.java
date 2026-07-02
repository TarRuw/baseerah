package com.baseerah.account;

import com.baseerah.account.dto.AccountDto;

/**
 * Maps {@link Account} entities to {@link AccountDto} projections. Pure and stateless. Critically, it
 * <strong>does not copy the raw {@code external_id}</strong> — only the {@code tokenizedAccountId} is
 * carried through, so the sensitive id can never leak to a caller (SAMA §9). Reads scalar fields only;
 * never touches the lazy {@code client}/{@code transactions} associations.
 */
public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountDto toDto(Account account) {
        return new AccountDto(
                account.getId(),
                account.getBankName(),
                account.getDisplayColor(),
                account.getCurrency(),
                account.getLatestBalance(),
                account.getTokenizedAccountId());
    }
}
