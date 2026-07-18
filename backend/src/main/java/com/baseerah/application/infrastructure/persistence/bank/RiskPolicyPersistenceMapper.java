package com.baseerah.application.infrastructure.persistence.bank;

import com.baseerah.domain.bank.RiskPolicy;

/**
 * Projects the persisted {@link RiskPolicyJpaEntity} singleton to its immutable domain {@link RiskPolicy}
 * view, so the underwriting rule, portfolio screening and the web-layer compliance surface all read policy
 * values without the JPA entity crossing the boundary (Global Rules). Pure and static.
 */
public final class RiskPolicyPersistenceMapper {

    private RiskPolicyPersistenceMapper() {
    }

    /** The domain view of the live risk policy. */
    public static RiskPolicy toDomain(RiskPolicyJpaEntity entity) {
        return new RiskPolicy(entity.getStaminaFloor(), entity.getAutoDeclineThreshold(),
                entity.isNdmoResidency(), entity.isTokenization(), entity.getSamaLastSync());
    }
}
