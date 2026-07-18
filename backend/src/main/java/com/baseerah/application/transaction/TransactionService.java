package com.baseerah.application.transaction;

import com.baseerah.api.transaction.dto.TransactionDto;
import com.baseerah.application.client.ClientService;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.infrastructure.persistence.transaction.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for transaction history (DESIGN.md §6). Delegates {@code {id}}
 * resolution to {@link ClientService} (shared 404 contract), then returns a page of the client's
 * transactions ordered newest-first.
 *
 * <p>Ordering is enforced here — not left to the caller — so the "newest-first" contract holds for every
 * request regardless of query params. Page size is clamped to {@link #MAX_PAGE_SIZE} to cap the response
 * payload; the freelancer persona alone has 300 transactions.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    /** Default page size when the caller supplies none. */
    public static final int DEFAULT_PAGE_SIZE = 50;
    /** Hard upper bound on page size, to cap payload size. */
    public static final int MAX_PAGE_SIZE = 200;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "bookingDate");

    private final ClientService clientService;
    private final TransactionRepository transactionRepository;

    public TransactionService(ClientService clientService,
            TransactionRepository transactionRepository) {
        this.clientService = clientService;
        this.transactionRepository = transactionRepository;
    }

    /**
     * A page of {@code clientId}'s transactions, newest-first, as DTOs. Validates the client exists
     * first (→ 404). {@code page} is floored at 0 and {@code size} is clamped to
     * {@code [1, MAX_PAGE_SIZE]}, so out-of-range params degrade gracefully rather than erroring.
     */
    public Page<TransactionDto> transactionsForClient(String clientId, int page, int size) {
        ClientJpaEntity client = clientService.requireClient(clientId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), NEWEST_FIRST);
        return transactionRepository.findByAccount_Client_Id(client.getId(), pageable)
                .map(TransactionWebMapper::toDto);
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
