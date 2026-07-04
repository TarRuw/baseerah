package com.baseerah.rescue;

import java.math.BigDecimal;
import java.util.List;

/**
 * API view of a Smart Rescue assessment (FR-06/07, DESIGN.md §5.4), serialized inside the shared success
 * envelope by {@code GET /api/v1/clients/{id}/rescue} and consumed by the Step 4.3 Rescue screen. Mapped
 * from the internal {@link RescueAssessment}; that domain record never crosses the controller boundary.
 *
 * <p>The no-deficit (healthy persona) case is an explicit <em>state</em>, not an error: {@code hasDeficit}
 * is {@code false}, {@code deficitInDays}/{@code shortfall} are {@code null}, and {@code options} is empty —
 * so the UI can render a clean "no action needed" banner rather than handling a 4xx. When a deficit exists,
 * {@code alertRaised} is the FR-06 15-day-lead trigger (honestly {@code false} for the freelancer on the
 * frozen mock — see the Step 4.1 handoff for the user-approved faithful-engine realignment).
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

    /** Map an internal {@link RescueAssessment} to its wire view, collapsing the no-deficit case to nulls. */
    public static RescueResponse from(RescueAssessment assessment) {
        if (!assessment.hasDeficit()) {
            return new RescueResponse(false, null, false, null, List.of());
        }
        List<RescueOptionDto> options = assessment.options().stream()
                .map(RescueOptionDto::from)
                .toList();
        return new RescueResponse(true, assessment.deficitInDays(), assessment.alertRaised(),
                assessment.predictedShortfall(), options);
    }
}
