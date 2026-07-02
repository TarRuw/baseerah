package com.baseerah.client;

import com.baseerah.client.dto.ClientDto;

/**
 * Maps {@link Client} entities to {@link ClientDto} projections. Pure and stateless — the single point
 * where a client entity becomes a client DTO, so no entity crosses the controller boundary (ORCHESTRATION
 * global rule). Reads scalar fields only; never touches the lazy {@code accounts} association.
 */
public final class ClientMapper {

    private ClientMapper() {
    }

    public static ClientDto toDto(Client client) {
        return new ClientDto(
                client.getId(),
                client.getExternalId(),
                client.getProfileLabel(),
                client.getPersona(),
                client.getCreatedAt());
    }
}
