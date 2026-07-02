package com.baseerah.client.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Client-facing view of a seeded persona (DESIGN.md §6, {@code GET /api/v1/clients}).
 *
 * <p>Immutable projection of the {@code Client} entity — the entity itself never leaves the service
 * layer. The {@code externalId} is the human-readable persona key (e.g. {@code client_001_family}) and
 * is safe to expose; only the <em>account</em> external id is sensitive (see {@code AccountDto}).
 *
 * @param id           canonical UUID primary key
 * @param externalId   stable, human-readable persona key
 * @param profileLabel display label (the Arabic {@code client_profile} from the mock feed)
 * @param persona      persona classification
 * @param createdAt    when the persona was first seeded
 */
public record ClientDto(
        UUID id,
        String externalId,
        String profileLabel,
        String persona,
        Instant createdAt) {
}
