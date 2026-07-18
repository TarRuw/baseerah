package com.baseerah.application.infrastructure.persistence.client;

import com.baseerah.application.infrastructure.persistence.account.AccountJpaEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A seeded end-user (one of the five {@code data-mocks/} personas). Maps the {@code clients} table
 * (DESIGN.md §4.2). Persistence-only: business logic lives in services, and this entity never leaves
 * the service layer (mapped to DTOs in the api layer).
 */
@Entity
@Table(name = "clients")
public class ClientJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "profile_label")
    private String profileLabel;

    @Column(name = "persona")
    private String persona;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccountJpaEntity> accounts = new ArrayList<>();

    protected ClientJpaEntity() {
        // JPA
    }

    public ClientJpaEntity(String externalId, String profileLabel, String persona) {
        this.externalId = externalId;
        this.profileLabel = profileLabel;
        this.persona = persona;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getProfileLabel() {
        return profileLabel;
    }

    public void setProfileLabel(String profileLabel) {
        this.profileLabel = profileLabel;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<AccountJpaEntity> getAccounts() {
        return accounts;
    }
}
