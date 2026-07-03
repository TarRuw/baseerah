package com.baseerah.forecast;

import com.baseerah.client.Client;
import com.baseerah.client.ClientService;
import com.baseerah.forecast.ForecastEngine.ForecastResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side application service for cash-flow forecasting (FR-04, DESIGN.md §5.2, §6). Backs
 * {@code GET /api/v1/clients/{id}/forecast}: it resolves the client — reusing {@link ClientService}'s
 * single 404 contract so the not-found behaviour matches every other client-scoped endpoint — then
 * projects the balance forward through the injected {@link ForecastEngine} <em>interface</em> (never the
 * concrete {@code HeuristicForecast}; Global Rule: engines stay behind interfaces) and maps the domain
 * {@link ForecastResult} to the enveloped {@link ForecastResponse}.
 *
 * <p>Keeping this behind a thin service (rather than in the controller) matches the layering convention —
 * {@code controller → service → engine}, mirroring {@link com.baseerah.stress.StressScoreService}. The
 * projection is computed on demand; the {@link HeuristicForecast} core walks only the trailing window plus
 * the horizon, so a single request stays comfortably inside the 2.5 s NFR (DESIGN.md §9) without persisting
 * a snapshot.
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
     * Project a client's balance over {@code horizonDays} and return it as an API DTO. Resolves the client
     * (→ 404 via {@link ClientService#requireClient} for an unknown or malformed id), runs the engine, and
     * maps the result — deriving {@code trend} — via {@link ForecastResponse#from}.
     *
     * @param clientId    canonical client UUID (as a string)
     * @param horizonDays days to project forward (30 = home chart; 90/180/365 = 3/6/12-month scenarios)
     * @return the enveloped-ready {@link ForecastResponse}
     */
    @Transactional(readOnly = true)
    public ForecastResponse forecastFor(String clientId, int horizonDays) {
        Client client = clientService.requireClient(clientId);
        ForecastResult result = forecastEngine.project(client.getId(), horizonDays);
        return ForecastResponse.from(result);
    }
}
