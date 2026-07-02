package com.baseerah.account;

import com.baseerah.account.dto.AccountDto;
import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import java.util.List;
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

    public AccountService(ClientService clientService, AccountRepository accountRepository) {
        this.clientService = clientService;
        this.accountRepository = accountRepository;
    }

    /** Accounts belonging to {@code clientId}, as DTOs. Validates the client exists first (→ 404). */
    public List<AccountDto> accountsForClient(String clientId) {
        Client client = clientService.requireClient(clientId);
        return accountRepository.findByClientId(client.getId()).stream()
                .map(AccountMapper::toDto)
                .toList();
    }
}
