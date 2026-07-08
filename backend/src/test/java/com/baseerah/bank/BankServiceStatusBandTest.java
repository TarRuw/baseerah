package com.baseerah.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseerah.bank.dto.PortfolioDto.Status;
import org.junit.jupiter.api.Test;

/**
 * Pure unit coverage for the Portfolio monitoring status bands (UI-06 fix, Step 8.4). The status shown next
 * to a facility's <em>health</em> number is derived from that same health score so the badge and the figure
 * can never disagree (previously it was mapped from the §5.5 verdict, which folds in DTI — a facility at
 * health 98 could read "Watch"). Bands are §5.5-aligned: {@code HEALTHY} at/above the OK stamina floor (70),
 * {@code AT_RISK} at/below the BAD stamina ceiling (48), {@code WATCH} in between.
 *
 * <p>No Spring context / Postgres — {@link BankService#statusFor(int)} is a pure static mapping, so these run
 * always (unlike the DB-gated {@code BankControllerTest}).
 */
class BankServiceStatusBandTest {

    @Test
    void healthAtHealthyFloorIsHealthy() {
        assertThat(BankService.statusFor(70)).isEqualTo(Status.HEALTHY);
    }

    @Test
    void healthWellAboveFloorIsHealthy() {
        assertThat(BankService.statusFor(98)).isEqualTo(Status.HEALTHY);
        assertThat(BankService.statusFor(100)).isEqualTo(Status.HEALTHY);
    }

    @Test
    void healthJustBelowHealthyFloorIsWatch() {
        assertThat(BankService.statusFor(69)).isEqualTo(Status.WATCH);
    }

    @Test
    void healthAtWatchFloorIsWatch() {
        assertThat(BankService.statusFor(49)).isEqualTo(Status.WATCH);
    }

    @Test
    void healthAtAtRiskCeilingIsAtRisk() {
        assertThat(BankService.statusFor(48)).isEqualTo(Status.AT_RISK);
    }

    @Test
    void healthWellBelowCeilingIsAtRisk() {
        assertThat(BankService.statusFor(0)).isEqualTo(Status.AT_RISK);
    }
}
