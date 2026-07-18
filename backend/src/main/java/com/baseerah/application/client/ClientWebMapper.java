package com.baseerah.application.client;

import com.baseerah.api.client.dto.ClientDto;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;

/**
 * Maps {@link ClientJpaEntity} entities to {@link ClientDto} projections. Pure and stateless — the single
 * point where a client entity becomes a client DTO, so no entity crosses the controller boundary
 * (ORCHESTRATION global rule). Reads scalar fields only; never touches the lazy {@code accounts}
 * association.
 *
 * <p>Lives in the application layer (not api): it is the anemic-slice edge where a persistence entity
 * becomes a web DTO, so the api layer never depends on a JPA entity (Phase 10.11 dependency rule).
 */
public final class ClientWebMapper {

    private ClientWebMapper() {
    }

    public static ClientDto toDto(ClientJpaEntity client) {
        return new ClientDto(
                client.getId(),
                client.getExternalId(),
                client.getProfileLabel(),
                client.getPersona(),
                client.getCreatedAt());
    }
}
