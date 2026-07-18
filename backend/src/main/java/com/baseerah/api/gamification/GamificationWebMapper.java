package com.baseerah.api.gamification;

import com.baseerah.api.gamification.dto.ChallengeDto;
import com.baseerah.api.gamification.dto.ClaimResponse;
import com.baseerah.api.gamification.dto.RewardsDto;
import com.baseerah.application.gamification.RewardsService.ClaimResult;
import com.baseerah.domain.gamification.ChallengeView;
import com.baseerah.domain.gamification.RewardsView;
import com.baseerah.shared.Messages;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Pure, static projection of the gamification application-layer values (domain views + the claim result) to
 * the wire DTOs — the {@code api/gamification} analogue of {@code StressWebMapper}/{@code RescueWebMapper}.
 * This mapper owns <strong>all</strong> gamification localization: the {@link ChallengeView}'s
 * title/subtitle message <em>keys</em> and the "{@code 541 / 2,000 SAR}" progress string are resolved here
 * from the request {@link Locale} (via the {@code shared} {@link Messages} seam), so the application service
 * stays locale-agnostic (Step 10.6 precedent — the service never imports {@code Messages}).
 */
public final class GamificationWebMapper {

    private GamificationWebMapper() {
    }

    /** Map a client's domain challenge views to their localized wire DTOs. */
    public static List<ChallengeDto> toChallengeDtos(List<ChallengeView> views, Messages messages,
            Locale locale) {
        return views.stream().map(view -> toChallengeDto(view, messages, locale)).toList();
    }

    /**
     * Map one domain {@link ChallengeView} to its wire DTO, resolving the title/subtitle from their message
     * keys (with the per-locale category label as arg 0 and the stored numeric args after) and building the
     * SAR-labelled progress text for the request locale.
     */
    public static ChallengeDto toChallengeDto(ChallengeView view, Messages messages, Locale locale) {
        Object[] args = titleArgs(view, messages, locale);
        String title = messages.get(locale, view.titleKey(), args);
        String subtitle = view.subtitleKey() == null ? null
                : messages.get(locale, view.subtitleKey(), args);
        return new ChallengeDto(view.id(), view.icon(), title, subtitle, view.reward(), view.pct(),
                progressText(view.current(), view.target(), messages, locale), view.claimable(), view.claimed());
    }

    /** The client's points balance + tier as its wire DTO (no localization — a plain projection). */
    public static RewardsDto toRewardsDto(RewardsView view) {
        return new RewardsDto(view.balance(), view.tier().name());
    }

    /**
     * The claim response: the client's post-award balance and tier from the {@link ClaimResult}, plus the
     * just-claimed challenge in its updated state (localized like any other challenge).
     */
    public static ClaimResponse toClaimResponse(ClaimResult result, ChallengeView challenge, Messages messages,
            Locale locale) {
        return new ClaimResponse(result.balance(), result.tier().name(),
                toChallengeDto(challenge, messages, locale));
    }

    /**
     * Assemble the message arguments for a challenge's title/subtitle: {@code arg[0]} is the category label
     * resolved for {@code locale} (COPY-01 grammar fix — e.g. "restaurant dining" / "المطاعم"), and
     * {@code arg[1..]} are the challenge's pre-formatted numeric arguments (Western digits, so figures are
     * identical across locales). Templates reference only the positions they need.
     */
    private static Object[] titleArgs(ChallengeView view, Messages messages, Locale locale) {
        String label = view.categoryTrigger() == null ? ""
                : messages.get(locale, "category." + view.categoryTrigger());
        String raw = view.textArgs();
        String[] nums = (raw == null || raw.isEmpty()) ? new String[0] : raw.split("\\|", -1);
        Object[] args = new Object[nums.length + 1];
        args[0] = label;
        System.arraycopy(nums, 0, args, 1, nums.length);
        return args;
    }

    /**
     * Human-readable progress, e.g. {@code "541 / 2,000 SAR"} — thousands-grouped whole SAR to match the
     * DESIGN §8 {@code fmt(n)} number helper, with the currency label localised for the request:
     * {@code "SAR"} by default and {@code "ريال"} when the {@code Accept-Language} header is Arabic. Digits
     * stay Western (the Goals screen re-formats for full RTL display in Step 5.3).
     */
    static String progressText(BigDecimal current, BigDecimal target, Messages messages, Locale locale) {
        return grouped(current) + " / " + grouped(target) + " " + messages.get(locale, "currency.sar");
    }

    private static String grouped(BigDecimal value) {
        long whole = (value == null ? BigDecimal.ZERO : value).setScale(0, RoundingMode.HALF_UP).longValue();
        return String.format(Locale.US, "%,d", whole);
    }
}
