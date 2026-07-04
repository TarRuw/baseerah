package com.baseerah.gamification;

import com.baseerah.client.Client;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A gamified micro-saving goal generated for one client (DESIGN.md §4.2, §5.6). Challenges are tailored to
 * the client's real spending anomalies by {@link ChallengeService}, so {@code categoryTrigger} records the
 * category the goal was derived from and {@code targetValue} is sized from the client's own telemetry —
 * never a prototype constant. Generation is idempotent through the {@code (client_id, code)} unique key, so
 * {@code code} is the stable per-client identity of a goal (re-running boot upserts rather than duplicates).
 *
 * <p>Persistence-only: like the other entities it never leaves the service layer — Step 5.2 maps it to a
 * DTO. Its progress lives in the companion {@link ChallengeProgress} row (one per challenge).
 */
@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subtitle")
    private String subtitle;

    @Column(name = "icon")
    private String icon;

    @Column(name = "target_value", nullable = false)
    private BigDecimal targetValue;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints;

    @Column(name = "category_trigger")
    private String categoryTrigger;

    protected Challenge() {
        // JPA
    }

    public Challenge(Client client, String code, String title, String subtitle, String icon,
            BigDecimal targetValue, int rewardPoints, String categoryTrigger) {
        this.client = client;
        this.code = code;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.targetValue = targetValue;
        this.rewardPoints = rewardPoints;
        this.categoryTrigger = categoryTrigger;
    }

    /**
     * Refresh the presentation + sizing of an existing goal from freshly-detected telemetry, keeping the
     * row's identity ({@code id}, {@code code}) and its {@link ChallengeProgress} claim state intact. Used
     * by idempotent re-generation on boot.
     */
    public void refresh(String title, String subtitle, String icon, BigDecimal targetValue,
            int rewardPoints, String categoryTrigger) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.targetValue = targetValue;
        this.rewardPoints = rewardPoints;
        this.categoryTrigger = categoryTrigger;
    }

    public UUID getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getIcon() {
        return icon;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public String getCategoryTrigger() {
        return categoryTrigger;
    }
}
