package com.baseerah.application.client;

import com.baseerah.api.client.dto.ClientDto;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.infrastructure.persistence.client.ClientRepository;
import com.baseerah.shared.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for clients (DESIGN.md §6). Owns the single {@code {id}} → client
 * resolution used by every {@code /api/v1/clients/{id}/...} endpoint, so the not-found contract lives in
 * exactly one place.
 *
 * <p>The API contract is <strong>strict UUID-only</strong> (see step 1.4 handoff): {@code {id}} must be a
 * canonical UUID. A malformed id or an unknown UUID both resolve to a {@link NotFoundException} → the
 * Step 0.4 global handler renders {@code 404 NOT_FOUND}. External ids are intentionally not accepted.
 */
@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    /** All seeded personas as DTOs, in the repository's natural order. */
    public List<ClientDto> list() {
        return clientRepository.findAll().stream()
                .map(ClientWebMapper::toDto)
                .toList();
    }

    /**
     * Resolve a client by its canonical UUID, or throw {@link NotFoundException} (→ 404). Returns the
     * entity for intra-service use (e.g. account/transaction lookups); the entity never leaves the
     * service layer. A non-UUID {@code id} is treated as "not found", keeping the strict contract.
     */
    public ClientJpaEntity requireClient(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Client not found: " + id);
        }
        return clientRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("Client not found: " + id));
    }

    /**
     * Resolve a client by its canonical UUID and return only its {@code id}, or throw
     * {@link NotFoundException} (→ 404). The id-only projection for api callers (e.g. the gamification
     * endpoint) that need the resolved identifier but must not touch the entity — the JPA entity never
     * leaves this service (Phase 10.11 dependency rule).
     */
    public UUID requireClientId(String id) {
        return requireClient(id).getId();
    }
}
