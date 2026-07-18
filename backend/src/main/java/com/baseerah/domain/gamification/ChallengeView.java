package com.baseerah.domain.gamification;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The immutable domain view of a challenge joined with its progress — what the application service returns to
 * the web layer (the gamification analogue of {@link com.baseerah.domain.stress.StressScore}). It carries only
 * typed facts: the message <em>keys</em>
 * ({@code titleKey}/{@code subtitleKey}) and their pre-formatted numeric {@code textArgs}, plus the raw
 * {@code current}/{@code target} SAR amounts. Presentation copy (the localized title/subtitle and the
 * "{@code 541 / 2,000 SAR}" progress string) is resolved from these by {@code GamificationWebMapper} using
 * the request locale, so this view stays framework-free and locale-neutral.
 *
 * @param id              the challenge id (claim target)
 * @param icon            a semantic icon token the screen maps to a glyph (e.g. {@code "restaurant"})
 * @param titleKey        message-bundle key for the goal's title
 * @param subtitleKey     message-bundle key for the goal's subtitle ({@code null} when absent)
 * @param textArgs        pipe-delimited, pre-formatted numeric args for the templates ({@code null} allowed)
 * @param categoryTrigger the category the goal was derived from, resolved to a label at read time ({@code null} allowed)
 * @param reward          the points awarded on claim
 * @param pct             completion percentage in {@code [0,100]}
 * @param current         the client's current SAR value toward the goal
 * @param target          the goal's SAR target
 * @param claimable       {@code true} when the reward can be claimed now ({@code pct >= 100 && !claimed})
 * @param claimed         whether the reward has already been claimed
 */
public record ChallengeView(UUID id, String icon, String titleKey, String subtitleKey, String textArgs,
        String categoryTrigger, int reward, int pct, BigDecimal current, BigDecimal target,
        boolean claimable, boolean claimed) {
}
