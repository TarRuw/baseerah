package com.baseerah.api.rescue;

import java.math.BigDecimal;
import java.util.List;

/**
 * API view of a Smart Rescue assessment (FR-06/07, DESIGN.md §5.4), serialized inside the shared success
 * envelope by {@code GET /api/v1/clients/{id}/rescue} and consumed by the Rescue screen. A plain carrier:
 * {@code RescueWebMapper} projects the domain {@code RescueAssessment} into it — that domain record never
 * crosses the controller boundary.
 *
 * <p>The no-deficit (healthy persona) case is an explicit <em>state</em>, not an error: {@code hasDeficit}
 * is {@code false}, {@code deficitInDays}/{@code shortfall} are {@code null}, and {@code options} is empty —
 * so the UI can render a clean "no action needed" banner rather than handling a 4xx. When a deficit exists,
 * {@code alertRaised} is the FR-06 15-day-lead trigger — {@code true} once the projected deficit falls inside
 * the 15-day window (the freelancer persona's imminent, computed deficit is the demo case).
 *
 * @param hasDeficit    whether a cash-flow deficit is projected within the assessment horizon
 * @param deficitInDays whole days until the projected deficit, or {@code null} when there is none
 * @param alertRaised   whether the deficit is inside the 15-day alert lead window (FR-06)
 * @param shortfall     SAR magnitude to bridge (positive), or {@code null} when there is no deficit
 * @param options       the bridge options offered — two when a deficit exists, empty otherwise
 */
public record RescueResponse(
        boolean hasDeficit,
        Integer deficitInDays,
        boolean alertRaised,
        BigDecimal shortfall,
        List<RescueOptionDto> options) {
}
