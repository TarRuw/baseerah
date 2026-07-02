package com.baseerah.stress;

import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for the Financial Stress Score (DESIGN.md §5.1, §6). Backs
 * {@code GET /api/v1/clients/{id}/stress-score}: it resolves the client — reusing {@link ClientService}'s
 * single 404 contract so the not-found behaviour matches every other client-scoped endpoint — then serves
 * the latest persisted daily snapshot, mapped to {@link StressScoreResponse}.
 *
 * <p>When a client has no snapshot yet (nothing writes one at boot), it lazily computes and persists the
 * first one via {@link StressScoreSnapshotWriter} (idempotent per day). Serving the persisted snapshot on
 * every subsequent request keeps the endpoint comfortably inside the 2.5 s NFR (DESIGN.md §9) — the full
 * window is scored at most once per client per day, not on each request.
 */
@Service
public class StressScoreService {

    private final ClientService clientService;
    private final StressScoreRepository stressScoreRepository;
    private final StressScoreSnapshotWriter snapshotWriter;

    public StressScoreService(ClientService clientService,
            StressScoreRepository stressScoreRepository, StressScoreSnapshotWriter snapshotWriter) {
        this.clientService = clientService;
        this.stressScoreRepository = stressScoreRepository;
        this.snapshotWriter = snapshotWriter;
    }

    /**
     * The latest stress-score snapshot for a client as an API DTO. Resolves the client (→ 404 via
     * {@link ClientService#requireClient} for an unknown or malformed id); serves the most recent snapshot,
     * or lazily computes and persists the first one when none exists yet.
     *
     * @param clientId canonical client UUID (as a string)
     * @return the enveloped-ready {@link StressScoreResponse} for the client's latest snapshot
     */
    @Transactional
    public StressScoreResponse latestFor(String clientId) {
        Client client = clientService.requireClient(clientId);
        StressScore snapshot = stressScoreRepository
                .findFirstByClientIdOrderByAsOfDateDesc(client.getId())
                .orElseGet(() -> snapshotWriter.writeSnapshot(clientId));
        return StressScoreResponse.from(snapshot);
    }
}
