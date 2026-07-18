package com.baseerah.api.loan;

import com.baseerah.domain.loan.LoanQuote;
import com.baseerah.domain.loan.LoanVerdict;
import com.baseerah.shared.Messages;
import java.math.BigDecimal;

/**
 * Projects the domain {@link LoanQuote} to its {@link LoanSimulateResponse} API view, resolving the two
 * <em>presentation</em> concerns the domain deliberately omits: the DESIGN §8 band colours and the
 * locale-resolved verdict text. Pure and static — the controller calls it directly, threading in the
 * {@link Messages} resolver for the request locale (Step 8.1, I18N-01), mirroring how {@code StressWebMapper}
 * owns the gauge colour.
 *
 * <p>The DTI banding thresholds ({@code <70% / <90%}) come from the prototype (the §6 endpoint contract did
 * not pin them); the prototype's pastel hexes are remapped to this canonical palette.
 */
public final class LoanWebMapper {

    // DESIGN §8 gauge/band palette — must stay identical to the Step 3.5 Flutter theme tokens.
    private static final String GREEN = "#1D9E63";
    private static final String ORANGE = "#E5A63A";
    private static final String RED = "#E0574F";

    // Verdict message keys (DESIGN §5.3), resolved to the request locale via Messages.
    private static final String VERDICT_COMFORTABLE = "loan.verdict.comfortable";
    private static final String VERDICT_STRAINS = "loan.verdict.strains";
    private static final String VERDICT_NOT_AFFORDABLE = "loan.verdict.notAffordable";

    // DTI band cut-offs (prototype): below 70% green, below 90% orange, else red.
    private static final int DTI_GREEN_MAX_PCT = 70;
    private static final int DTI_ORANGE_MAX_PCT = 90;

    private LoanWebMapper() {
    }

    /**
     * Project a domain quote to its API view, resolving band colours and the localized verdict text.
     *
     * @param quote    the domain result of {@code LoanCalculator.compute}
     * @param messages the request-locale message resolver
     */
    public static LoanSimulateResponse toResponse(LoanQuote quote, Messages messages) {
        return new LoanSimulateResponse(
                quote.installment(),
                quote.total(),
                quote.dti(),
                dtiColorFor(quote.dti()),
                messages.get(messageKeyFor(quote.verdict())),
                verdictColorFor(quote.verdict()),
                quote.projectedScore());
    }

    /** Map a DTI ratio to its DESIGN §8 band colour: {@code <70% green, <90% orange, else red} (prototype). */
    private static String dtiColorFor(BigDecimal dti) {
        long dtiPct = Math.round(dti.doubleValue() * 100.0);
        if (dtiPct < DTI_GREEN_MAX_PCT) {
            return GREEN;
        }
        return dtiPct < DTI_ORANGE_MAX_PCT ? ORANGE : RED;
    }

    /** The DESIGN §8 band colour for a verdict (green / orange / red). */
    private static String verdictColorFor(LoanVerdict verdict) {
        return switch (verdict) {
            case COMFORTABLE -> GREEN;
            case STRAINS -> ORANGE;
            case NOT_AFFORDABLE -> RED;
        };
    }

    /** The {@code messages*.properties} key for a verdict (DESIGN §5.3). */
    private static String messageKeyFor(LoanVerdict verdict) {
        return switch (verdict) {
            case COMFORTABLE -> VERDICT_COMFORTABLE;
            case STRAINS -> VERDICT_STRAINS;
            case NOT_AFFORDABLE -> VERDICT_NOT_AFFORDABLE;
        };
    }
}
