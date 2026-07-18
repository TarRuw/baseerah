package com.baseerah.api.gamification;

import com.baseerah.api.gamification.dto.ChallengeDto;
import com.baseerah.api.gamification.dto.ClaimResponse;
import com.baseerah.api.gamification.dto.RewardsDto;
import com.baseerah.application.gamification.ChallengeService;
import com.baseerah.application.gamification.RewardsService;
import com.baseerah.application.gamification.RewardsService.ClaimResult;
import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.application.client.ClientService;
import com.baseerah.domain.gamification.ChallengeView;
import com.baseerah.domain.gamification.RewardsView;
import com.baseerah.shared.ApiResponse;
import com.baseerah.shared.Messages;
import com.baseerah.shared.NotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gamified micro-saving endpoints (FR-09/10, DESIGN.md §6). Thin: it resolves the path ids, delegates to
 * {@link ChallengeService} (list / project challenges) and {@link RewardsService} (points balance + the
 * guarded claim), resolves the domain views to wire DTOs via {@link GamificationWebMapper} (which owns the
 * {@code Accept-Language} localization), and wraps every result in the shared {@link ApiResponse} envelope —
 * the claim guard and all sizing stay in the services (ORCHESTRATION Global Rules). Three routes mirror
 * DESIGN §6:
 *
 * <ul>
 *   <li>{@code GET  /api/v1/clients/{id}/challenges} — the client's challenges with progress.</li>
 *   <li>{@code POST /api/v1/clients/{id}/challenges/{cid}/claim} — claim a completed challenge; returns the
 *       new balance/tier and the challenge in its claimed state. A double or incomplete claim surfaces as the
 *       shared {@code 409 CONFLICT} envelope (the rule lives in {@link RewardsService}).</li>
 *   <li>{@code GET  /api/v1/clients/{id}/rewards} — points balance + tier.</li>
 * </ul>
 *
 * <p>Path ids are bound as raw strings and resolved to {@code UUID}s here; a malformed or unknown client id
 * yields the shared {@code 404 NOT_FOUND} envelope (matching every other client-scoped controller), and a
 * malformed or foreign challenge id likewise resolves to {@code 404}. {@code Accept-Language} is resolved into
 * the injected {@link Locale} and passed to the web mapper so the progress text's currency label is localised.
 */
@RestController
@RequestMapping("/api/v1/clients")
public class GamificationController {

    private final ChallengeService challengeService;
    private final RewardsService rewardsService;
    private final ClientService clientService;
    private final OwnershipGuard ownershipGuard;
    private final Messages messages;

    public GamificationController(ChallengeService challengeService, RewardsService rewardsService,
            ClientService clientService, OwnershipGuard ownershipGuard, Messages messages) {
        this.challengeService = challengeService;
        this.rewardsService = rewardsService;
        this.clientService = clientService;
        this.ownershipGuard = ownershipGuard;
        this.messages = messages;
    }

    /** {@code GET /api/v1/clients/{id}/challenges} — the client's challenges, each with its progress. */
    @GetMapping("/{id}/challenges")
    public ApiResponse<List<ChallengeDto>> challenges(@PathVariable String id, Locale locale) {
        ownershipGuard.assertOwns(id);
        List<ChallengeView> views = challengeService.listForClient(clientId(id));
        return ApiResponse.ok(GamificationWebMapper.toChallengeDtos(views, messages, locale));
    }

    /** {@code POST /api/v1/clients/{id}/challenges/{cid}/claim} — claim a completed challenge. */
    @PostMapping("/{id}/challenges/{cid}/claim")
    public ApiResponse<ClaimResponse> claim(@PathVariable String id, @PathVariable String cid, Locale locale) {
        ownershipGuard.assertOwns(id);
        UUID clientId = clientId(id);
        UUID challengeId = challengeId(cid);
        ClaimResult result = rewardsService.claimChallenge(clientId, challengeId);
        ChallengeView challenge = challengeService.challengeViewFor(clientId, challengeId);
        return ApiResponse.ok(GamificationWebMapper.toClaimResponse(result, challenge, messages, locale));
    }

    /** {@code GET /api/v1/clients/{id}/rewards} — the client's points balance + tier. */
    @GetMapping("/{id}/rewards")
    public ApiResponse<RewardsDto> rewards(@PathVariable String id) {
        ownershipGuard.assertOwns(id);
        RewardsView summary = rewardsService.summaryFor(requireClientId(id));
        return ApiResponse.ok(GamificationWebMapper.toRewardsDto(summary));
    }

    /**
     * Resolve the path {@code id} to a client-scoped {@code UUID}, mapping a malformed value to the shared
     * {@code 404}. Existence of a well-formed id is enforced downstream (the challenge/list services apply the
     * same strict-UUID contract), so this only guards the parse.
     */
    private static UUID clientId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Client not found: " + id);
        }
    }

    /** Parse the challenge path id; a malformed value is a {@code 404} (no such challenge). */
    private static UUID challengeId(String cid) {
        try {
            return UUID.fromString(cid);
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Challenge not found: " + cid);
        }
    }

    /**
     * Resolve and <em>verify</em> the client exists, returning its {@code UUID}. Used by the rewards endpoint,
     * whose {@link RewardsService#summaryFor} reads the ledger directly (a never-seeded client would otherwise
     * report a bare zero balance instead of a {@code 404}).
     */
    private UUID requireClientId(String id) {
        return clientService.requireClientId(id);
    }
}
