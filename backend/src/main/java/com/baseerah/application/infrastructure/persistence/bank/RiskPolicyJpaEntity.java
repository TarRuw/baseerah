package com.baseerah.application.infrastructure.persistence.bank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The bank-wide risk policy (DESIGN.md §4.2, §7.7) — a singleton feeding the risk-policy endpoints and the
 * Settings screen. Its one row is created by migration {@code 006-bank.sql}; the schema's {@code singleton}
 * guard column (unmapped here) makes a second row impossible, so
 * {@link RiskPolicyRepository#loadPolicy()} always resolves exactly one live policy.
 *
 * <p>Persistence-only (mapped to a domain {@code RiskPolicy} / web DTO). {@code staminaFloor} /
 * {@code autoDeclineThreshold} mirror the §5.5 verdict guardrails; {@code ndmoResidency} and
 * {@code tokenization} are the compliance toggles; {@code samaLastSync} stamps the last SAMA Open-Banking
 * sync shown in Settings.
 */
@Entity
@Table(name = "risk_policy")
public class RiskPolicyJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "stamina_floor", nullable = false)
    private int staminaFloor;

    @Column(name = "auto_decline_threshold", nullable = false)
    private int autoDeclineThreshold;

    @Column(name = "ndmo_residency", nullable = false)
    private boolean ndmoResidency;

    @Column(name = "tokenization", nullable = false)
    private boolean tokenization;

    @Column(name = "sama_last_sync")
    private Instant samaLastSync;

    protected RiskPolicyJpaEntity() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public int getStaminaFloor() {
        return staminaFloor;
    }

    public void setStaminaFloor(int staminaFloor) {
        this.staminaFloor = staminaFloor;
    }

    public int getAutoDeclineThreshold() {
        return autoDeclineThreshold;
    }

    public void setAutoDeclineThreshold(int autoDeclineThreshold) {
        this.autoDeclineThreshold = autoDeclineThreshold;
    }

    public boolean isNdmoResidency() {
        return ndmoResidency;
    }

    public void setNdmoResidency(boolean ndmoResidency) {
        this.ndmoResidency = ndmoResidency;
    }

    public boolean isTokenization() {
        return tokenization;
    }

    public void setTokenization(boolean tokenization) {
        this.tokenization = tokenization;
    }

    public Instant getSamaLastSync() {
        return samaLastSync;
    }

    public void setSamaLastSync(Instant samaLastSync) {
        this.samaLastSync = samaLastSync;
    }
}
