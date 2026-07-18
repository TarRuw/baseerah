package com.baseerah.application.account;

import com.baseerah.api.account.dto.AccountDto;
import com.baseerah.application.client.ClientService;
import com.baseerah.application.infrastructure.persistence.account.AccountRepository;
import com.baseerah.application.infrastructure.persistence.bankdirectory.BankJpaEntity;
import com.baseerah.application.infrastructure.persistence.bankdirectory.BankRepository;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for accounts (DESIGN.md §6). Delegates {@code {id}} resolution to
 * {@link ClientService} so the not-found (404) behaviour is identical across every client-scoped
 * endpoint, then maps the client's accounts to {@link AccountDto} (dropping the raw external id).
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final ClientService clientService;
    private final AccountRepository accountRepository;
    private final BankRepository bankRepository;

    public AccountService(ClientService clientService, AccountRepository accountRepository,
            BankRepository bankRepository) {
        this.clientService = clientService;
        this.accountRepository = accountRepository;
        this.bankRepository = bankRepository;
    }

    /** Accounts belonging to {@code clientId}, as DTOs. Validates the client exists first (→ 404). */
    public List<AccountDto> accountsForClient(String clientId) {
        ClientJpaEntity client = clientService.requireClient(clientId);
        // The whole directory is eight rows of reference data — one read, then an in-memory lookup per
        // account, rather than a query per row.
        Map<String, BankJpaEntity> byName = bankRepository.findAll().stream()
                .collect(Collectors.toMap(BankJpaEntity::getName, Function.identity()));
        return accountRepository.findByClientId(client.getId()).stream()
                .map(account -> AccountWebMapper.toDto(account, byName.get(account.getBankName())))
                .toList();
    }
}
