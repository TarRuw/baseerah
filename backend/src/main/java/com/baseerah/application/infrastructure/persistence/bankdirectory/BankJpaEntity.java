package com.baseerah.application.infrastructure.persistence.bankdirectory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A bank an account can be held at. Maps the {@code banks} table (changeset 013) — reference data, seeded
 * by the migration and never written at runtime.
 *
 * <p>Its own package rather than {@code persistence.bank}, which is the B2B loan portal's slice
 * (loan applications, risk policy) and has nothing to do with this directory.
 *
 * <p>{@link #getName()} is the join key: it matches {@code accounts.bank_name}, the display name the SAMA
 * feed carries. See 013 for why the link is by name rather than a foreign key.
 */
@Entity
@Table(name = "banks")
public class BankJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Stable identifier exposed to clients (e.g. {@code RAJHI}); never the display name. */
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    /** Frontend asset key: the bank's mark is bundled at {@code assets/banks/<logo_slug>.png}. */
    @Column(name = "logo_slug", nullable = false)
    private String logoSlug;

    @Column(name = "brand_color", nullable = false)
    private String brandColor;

    protected BankJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getNameAr() {
        return nameAr;
    }

    public String getLogoSlug() {
        return logoSlug;
    }

    public String getBrandColor() {
        return brandColor;
    }
}
