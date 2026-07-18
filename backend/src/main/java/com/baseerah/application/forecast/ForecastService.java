package com.baseerah.application.forecast;

import com.baseerah.application.infrastructure.gateway.forecast.ForecastEngine;
import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
import com.baseerah.application.client.ClientService;
import com.baseerah.domain.forecast.ForecastResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for cash-flow forecasting (FR-04, DESIGN.md §5.2, §6). Backs
 * {@code GET /api/v1/clients/{id}/forecast}: it resolves the client — reusing {@link ClientService}'s
 * single 404 contract so the not-found behaviour matches every other client-scoped endpoint — then
 * projects the balance forward through the injected {@link ForecastEngine} <em>gateway interface</em>
 * (never the concrete {@code HeuristicForecastEngine}; Global Rule: engines stay behind interfaces, and the
 * {@code @Primary} caching decorator fronts it) and returns the domain {@link ForecastResult}. The web
 * mapper turns that into the API response.
 *
 * <p>Keeping this behind a thin service (rather than in the controller) matches the layering convention —
 * {@code controller → service → gateway}, mirroring {@link com.baseerah.application.stress.StressScoreService}.
 * The projection is computed on demand; the projection core walks only the trailing window plus the horizon,
 * so a single request stays comfortably inside the 2.5 s NFR (DESIGN.md §9) without persisting a snapshot.
 */
@Service
public class ForecastService {

    private final ClientService clientService;
    private final ForecastEngine forecastEngine;

    public ForecastService(ClientService clientService, ForecastEngine forecastEngine) {
        this.clientService = clientService;
        this.forecastEngine = forecastEngine;
    }

    /**
     * Project a client's balance over {@code horizonDays} as a domain result. Resolves the client (→ 404 via
     * {@link ClientService#requireClient} for an unknown or malformed id) and runs the engine; the caller
     * (web mapper) derives the presentation {@code trend}.
     *
     * @param clientId    canonical client UUID (as a string)
     * @param horizonDays days to project forward (30 = home chart; 90/180/365 = 3/6/12-month scenarios)
     * @return the domain {@link ForecastResult}
     */
    @Transactional(readOnly = true)
    public ForecastResult forecastFor(String clientId, int horizonDays) {
        ClientJpaEntity client = clientService.requireClient(clientId);
        return forecastEngine.project(client.getId(), horizonDays);
    }
}
