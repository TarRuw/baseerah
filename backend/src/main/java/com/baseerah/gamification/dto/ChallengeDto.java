package com.baseerah.gamification.dto;

import java.util.UUID;

/**
 * A single gamified challenge as the Goals screen (Step 5.3, FR-09/10, DESIGN.md §5.6 / §7) consumes it —
 * the client-facing projection of a {@link com.baseerah.gamification.Challenge} joined with its
 * {@link com.baseerah.gamification.ChallengeProgress}. Entities never leave the service layer, so this is
 * built by {@link com.baseerah.gamification.ChallengeService} (ORCHESTRATION Global Rules).
 *
 * @param id           the challenge id (claim target)
 * @param icon         a semantic icon token the screen maps to a glyph (e.g. {@code "restaurant"})
 * @param title        the goal's short title
 * @param subtitle     the goal's descriptive line
 * @param reward       the points awarded on claim (the challenge's {@code reward_points})
 * @param pct          completion percentage in {@code [0,100]}
 * @param progressText a human-readable progress string, e.g. {@code "541 / 2,000 SAR"}
 * @param claimable    {@code true} when the reward can be claimed now ({@code pct >= 100 && !claimed})
 * @param claimed      whether the reward has already been claimed
 */
public record ChallengeDto(UUID id, String icon, String title, String subtitle, int reward, int pct,
        String progressText, boolean claimable, boolean claimed) {
}
