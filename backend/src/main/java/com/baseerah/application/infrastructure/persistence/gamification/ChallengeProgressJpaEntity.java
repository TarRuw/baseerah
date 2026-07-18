package com.baseerah.application.infrastructure.persistence.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One client's advance toward a {@link ChallengeJpaEntity} (DESIGN.md §4.2, §5.6). Exactly one row per
 * challenge (enforced by the {@code uq_challenge_progress_challenge} unique key), so it is modelled as a
 * {@link OneToOne}. {@code pct} is the completion percentage clamped to {@code [0,100]} (also DB-checked);
 * a challenge is completable when {@code pct >= 100} and not yet {@code claimed}.
 *
 * <p>A JPA type confined to the persistence package; the claim transition ({@link #markClaimed}) is the
 * single place that flips {@code claimed}/{@code claimedAt}, driven by the application {@code RewardsService}.
 * Telemetry refreshes on boot update {@code currentValue}/{@code pct} via {@link #updateProgress} but never
 * touch the claim state.
 */
@Entity
@Table(name = "challenge_progress")
public class ChallengeProgressJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false, unique = true)
    private ChallengeJpaEntity challenge;

    @Column(name = "current_value", nullable = false)
    private BigDecimal currentValue;

    @Column(name = "pct", nullable = false)
    private int pct;

    @Column(name = "claimed", nullable = false)
    private boolean claimed;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    protected ChallengeProgressJpaEntity() {
        // JPA
    }

    public ChallengeProgressJpaEntity(ChallengeJpaEntity challenge, BigDecimal currentValue, int pct) {
        this.challenge = challenge;
        this.currentValue = currentValue;
        this.pct = pct;
        this.claimed = false;
    }

    /** Refresh the tracked telemetry (idempotent boot re-generation); never touches the claim state. */
    public void updateProgress(BigDecimal currentValue, int pct) {
        this.currentValue = currentValue;
        this.pct = pct;
    }

    /** Whether this challenge is complete and can be claimed for its reward (only meaningful when unclaimed). */
    public boolean isComplete() {
        return pct >= 100;
    }

    /** Whether the reward can still be claimed: complete and not already claimed. */
    public boolean isClaimable() {
        return isComplete() && !claimed;
    }

    /** Flip to claimed at {@code when}. Caller ({@code RewardsService}) guards {@link #isClaimable()} first. */
    public void markClaimed(Instant when) {
        this.claimed = true;
        this.claimedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public ChallengeJpaEntity getChallenge() {
        return challenge;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public int getPct() {
        return pct;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }
}
