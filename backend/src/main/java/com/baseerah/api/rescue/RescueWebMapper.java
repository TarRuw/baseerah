package com.baseerah.api.rescue;

import com.baseerah.domain.rescue.RescueAssessment;
import com.baseerah.domain.rescue.RescueOption;
import com.baseerah.domain.rescue.RescueOutcome;
import com.baseerah.shared.Messages;
import java.util.List;

/**
 * Projects the domain Smart Rescue values to their API views (FR-06/07, DESIGN.md §5.4), resolving the one
 * <em>presentation</em> concern the domain deliberately omits: the locale-specific option labels/details and
 * the confirmation message (Step 8.1, I18N-01). Pure and static — the controller calls it directly, threading
 * in the {@link Messages} resolver for the request locale, mirroring how {@code LoanWebMapper} owns the
 * verdict text and {@code StressWebMapper} the gauge colour.
 */
public final class RescueWebMapper {

    // Message keys (DESIGN §5.4), resolved to the request locale via Messages.
    private static final String MURABAHA_LABEL = "rescue.option.murabaha.label";
    private static final String MURABAHA_DETAIL = "rescue.option.murabaha.detail";
    private static final String LIQUIDATE_LABEL = "rescue.option.liquidate.label";
    private static final String LIQUIDATE_DETAIL = "rescue.option.liquidate.detail";
    private static final String CONFIRM_MESSAGE = "rescue.confirm";

    private RescueWebMapper() {
    }

    /**
     * Project a domain assessment to its wire view, collapsing the no-deficit case to nulls and resolving each
     * option's localized label/detail.
     *
     * @param assessment the domain result of {@code RescueService.assess}
     * @param messages   the request-locale message resolver
     */
    public static RescueResponse toResponse(RescueAssessment assessment, Messages messages) {
        if (!assessment.hasDeficit()) {
            return new RescueResponse(false, null, false, null, List.of());
        }
        List<RescueOptionDto> options = assessment.options().stream()
                .map(option -> toOptionDto(option, messages))
                .toList();
        return new RescueResponse(true, assessment.deficitInDays(), assessment.alertRaised(),
                assessment.predictedShortfall(), options);
    }

    /**
     * Project a confirmed rescue to its wire view, resolving the localized confirmation message from the
     * chosen option and the before/after scores.
     *
     * @param outcome  the domain result of {@code RescueService.confirm}
     * @param chosen   the bridge the client confirmed (its type and amount fill the message)
     * @param messages the request-locale message resolver
     */
    public static RescueConfirmResponse toConfirmResponse(
            RescueOutcome outcome, RescueOption chosen, Messages messages) {
        String message = messages.get(CONFIRM_MESSAGE, chosen.type().name(),
                chosen.amount().toPlainString(),
                Integer.toString(outcome.scoreBefore()), Integer.toString(outcome.scoreAfter()));
        return new RescueConfirmResponse(outcome.scoreBefore(), outcome.scoreAfter(), message);
    }

    /** Map one domain option to its wire view, resolving the locale-specific label and detail copy. */
    private static RescueOptionDto toOptionDto(RescueOption option, Messages messages) {
        return switch (option.type()) {
            case MURABAHA -> new RescueOptionDto(option.type(),
                    messages.get(MURABAHA_LABEL),
                    option.amount(), option.term(),
                    messages.get(MURABAHA_DETAIL,
                            option.amount().toPlainString(), Integer.toString(option.term())));
            case LIQUIDATE -> new RescueOptionDto(option.type(),
                    messages.get(LIQUIDATE_LABEL),
                    option.amount(), option.term(),
                    messages.get(LIQUIDATE_DETAIL, option.amount().toPlainString()));
        };
    }
}
