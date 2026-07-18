package com.baseerah.application.stress;

import com.baseerah.application.infrastructure.persistence.stress.StressPersistenceMapper;
import com.baseerah.application.infrastructure.persistence.stress.StressScoreJpaEntity;
import com.baseerah.application.infrastructure.persistence.stress.StressScoreRepository;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.client.ClientService;
import com.baseerah.domain.stress.StressScore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for the Financial Stress Score (DESIGN.md §5.1, §6). Backs
 * {@code GET /api/v1/clients/{id}/stress-score}: it resolves the client — reusing {@link ClientService}'s
 * single 404 contract so the not-found behaviour matches every other client-scoped endpoint — then serves
 * the latest persisted daily snapshot, mapped to the domain {@link StressScore} (the JPA entity never
 * leaves this layer; the web mapper turns the domain value into the API response).
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
     * The latest stress-score snapshot for a client as a domain value. Resolves the client (→ 404 via
     * {@link ClientService#requireClient} for an unknown or malformed id); serves the most recent snapshot,
     * or lazily computes and persists the first one when none exists yet.
     *
     * @param clientId canonical client UUID (as a string)
     * @return the domain {@link StressScore} for the client's latest snapshot
     */
    @Transactional
    public StressScore latestFor(String clientId) {
        ClientJpaEntity client = clientService.requireClient(clientId);
        StressScoreJpaEntity snapshot = stressScoreRepository
                .findFirstByClientIdOrderByAsOfDateDesc(client.getId())
                .orElseGet(() -> snapshotWriter.writeSnapshot(clientId));
        return StressPersistenceMapper.toDomain(snapshot);
    }
}
