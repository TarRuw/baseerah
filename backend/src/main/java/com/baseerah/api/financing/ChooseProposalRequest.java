package com.baseerah.api.financing;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/clients/{id}/financing/requests/{rid}/choose} — the id of the proposal
 * the client accepts. Bound as a string and parsed to a strict {@code UUID} in the controller (a malformed
 * value maps to the shared 404, matching the path-id contract).
 *
 * @param proposalId the chosen proposal's id (a UUID as a string)
 */
public record ChooseProposalRequest(@NotBlank String proposalId) {
}
