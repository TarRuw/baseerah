package com.baseerah.config;

import com.baseerah.domain.bank.UnderwritingRule;
import com.baseerah.domain.loan.LoanCalculator;
import com.baseerah.domain.stress.StressScoreCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the pure, framework-free domain calculators as Spring beans so the application services can
 * have them injected without the domain layer depending on Spring. Part of the composition root
 * ({@code config}); ArchUnit excludes this package from the layering rules (step 10.11).
 *
 * <p>The stress pilot (step 10.2) starts this; later feature slices add their own calculators' beans here
 * as they are decoupled from Spring.
 */
@Configuration
public class DomainConfig {

    /** The §5.1 Financial Stress Score calculator — pure and stateless, safe as a singleton bean. */
    @Bean
    public StressScoreCalculator stressScoreCalculator() {
        return new StressScoreCalculator();
    }

    /** The §5.3 loan-affordability calculator — pure and stateless, safe as a singleton bean. */
    @Bean
    public LoanCalculator loanCalculator() {
        return new LoanCalculator();
    }

    /**
     * The §5.5 predictive underwriting rule — pure and stateless; it composes the stress calculator for the
     * income-stability sub-score, so the bean depends on {@link #stressScoreCalculator()}.
     */
    @Bean
    public UnderwritingRule underwritingRule(StressScoreCalculator stressScoreCalculator) {
        return new UnderwritingRule(stressScoreCalculator);
    }
}
