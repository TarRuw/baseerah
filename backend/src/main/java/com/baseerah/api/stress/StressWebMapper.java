package com.baseerah.api.stress;

import com.baseerah.domain.stress.StressScore;
import com.baseerah.domain.stress.Zone;

/**
 * Projects the domain {@link StressScore} snapshot to its {@link StressScoreResponse} API view, resolving
 * the zone's gauge colour (DESIGN.md §8) — the colour belongs to the web layer, not the domain. Pure and
 * static: the controller calls it directly.
 */
public final class StressWebMapper {

    /** Gauge zone colours (DESIGN.md §8) — must stay identical to the Flutter theme tokens. */
    private static final String OPTIMAL_HEX = "#1D9E63";
    private static final String WARNING_HEX = "#E5A63A";
    private static final String CRITICAL_HEX = "#E0574F";

    private StressWebMapper() {
    }

    /** Project a domain snapshot to its API view, resolving the zone's gauge colour. */
    public static StressScoreResponse toResponse(StressScore domain) {
        return new StressScoreResponse(
                domain.score(),
                domain.zone(),
                colorFor(domain.zone()),
                domain.spendingVelocity(),
                domain.incomeConsistency(),
                domain.liabilityRatio(), // domain keeps its internal names; the API field is obligationHealth
                domain.asOfDate());
    }

    /** Map a zone to its gauge hex (DESIGN.md §8). */
    private static String colorFor(Zone zone) {
        return switch (zone) {
            case OPTIMAL -> OPTIMAL_HEX;
            case WARNING -> WARNING_HEX;
            case CRITICAL -> CRITICAL_HEX;
        };
    }
}
