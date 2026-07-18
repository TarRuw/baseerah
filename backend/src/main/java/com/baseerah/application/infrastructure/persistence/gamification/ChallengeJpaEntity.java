package com.baseerah.application.infrastructure.persistence.gamification;

import com.baseerah.application.infrastructure.persistence.client.ClientJpaEntity;
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
 * Persistence entity for a gamified micro-saving goal generated for one client (DESIGN.md §4.2, §5.6).
 * Challenges are tailored to the client's real spending anomalies by the application {@code ChallengeService}
 * (which invokes the pure {@code ChallengeProgressRule}), so {@code categoryTrigger} records the category the
 * goal was derived from and {@code targetValue} is sized from the client's own telemetry — never a prototype
 * constant. Generation is idempotent through the {@code (client_id, code)} unique key, so {@code code} is the
 * stable per-client identity of a goal (re-running boot upserts rather than duplicates).
 *
 * <p>A JPA type confined to the persistence package — mapped to the domain {@code ChallengeView} by
 * {@link ChallengePersistenceMapper} before it crosses a layer boundary. Its progress lives in the companion
 * {@link ChallengeProgressJpaEntity} row (one per challenge). The {@code @ManyToOne} target is still the
 * pre-10.9 {@link ClientJpaEntity} entity.
 */
@Entity
@Table(name = "challenges")
public class ChallengeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

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

    /**
     * Pipe-delimited, pre-formatted numeric arguments the {@code title}/{@code subtitle} message templates
     * interpolate (Step 8.1, I18N-01). Stored locale-neutral (Western digits) and resolved with the request
     * locale + the category label in the web mapper. May be {@code null} when a template takes no numeric argument.
     */
    @Column(name = "text_args")
    private String textArgs;

    protected ChallengeJpaEntity() {
        // JPA
    }

    public ChallengeJpaEntity(ClientJpaEntity client, String code, String title, String subtitle, String icon,
            BigDecimal targetValue, int rewardPoints, String categoryTrigger, String textArgs) {
        this.client = client;
        this.code = code;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.targetValue = targetValue;
        this.rewardPoints = rewardPoints;
        this.categoryTrigger = categoryTrigger;
        this.textArgs = textArgs;
    }

    /**
     * Refresh the presentation + sizing of an existing goal from freshly-detected telemetry, keeping the
     * row's identity ({@code id}, {@code code}) and its {@link ChallengeProgressJpaEntity} claim state intact.
     * Used by idempotent re-generation on boot.
     */
    public void refresh(String title, String subtitle, String icon, BigDecimal targetValue,
            int rewardPoints, String categoryTrigger, String textArgs) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.targetValue = targetValue;
        this.rewardPoints = rewardPoints;
        this.categoryTrigger = categoryTrigger;
        this.textArgs = textArgs;
    }

    public UUID getId() {
        return id;
    }

    public ClientJpaEntity getClient() {
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

    public String getTextArgs() {
        return textArgs;
    }
}
