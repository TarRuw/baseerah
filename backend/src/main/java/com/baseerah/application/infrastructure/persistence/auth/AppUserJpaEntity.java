package com.baseerah.application.infrastructure.persistence.auth;

import com.baseerah.domain.auth.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A sign-in identity for phone + OTP authentication (DESIGN.md §12). Maps the {@code app_users} table
 * (Liquibase {@code 010-auth.sql}). No password is stored — a user is a mobile number, a display name
 * (EN + AR), a {@link Role}, and, for consumers, a link to their persona.
 *
 * <p>The persona link is modelled as the raw {@code clients.id} UUID rather than a {@code @ManyToOne} to
 * {@code ClientJpaEntity}, so a user is resolvable to its persona without coupling the two aggregates.
 * Persistence-only: business logic lives in services; mapped to projections at the edge by
 * {@link AppUserPersistenceMapper}.
 */
@Entity
@Table(name = "app_users")
public class AppUserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "mobile", nullable = false, unique = true)
    private String mobile;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "display_name_ar", nullable = false)
    private String displayNameAr;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    /** {@code clients.id} for a consumer; {@code null} for a bank officer. */
    @Column(name = "client_id")
    private UUID clientId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUserJpaEntity() {
        // JPA
    }

    public AppUserJpaEntity(String mobile, String displayName, String displayNameAr, Role role,
            UUID clientId) {
        this.mobile = mobile;
        this.displayName = displayName;
        this.displayNameAr = displayNameAr;
        this.role = role;
        this.clientId = clientId;
    }

    public UUID getId() {
        return id;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayNameAr() {
        return displayNameAr;
    }

    public void setDisplayNameAr(String displayNameAr) {
        this.displayNameAr = displayNameAr;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
