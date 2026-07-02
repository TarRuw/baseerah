package com.baseerah.transaction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Transaction}. Transactions are queried through the account→client
 * relation rather than a denormalised {@code client_id}, so finders traverse {@code account.client.id}.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** All of a client's transactions booked within {@code [from, to]} — the window scoring/forecasting reads. */
    List<Transaction> findByAccount_Client_IdAndBookingDateBetween(UUID clientId, Instant from, Instant to);

    /** Paged history for a client (Step 1.4 transaction list), ordered by the caller-supplied {@link Pageable}. */
    Page<Transaction> findByAccount_Client_Id(UUID clientId, Pageable pageable);
}
