/**
 * Persistence infrastructure (part of the application layer). JPA entities, Spring Data repositories, and
 * persistence mappers (entity ⇄ domain record) live here.
 *
 * <p>Same layer as the application services that use it. Depends on {@code domain}; a service maps entities
 * to domain records before invoking a pure domain rule. Filled by the feature slices (steps 10.2+).
 */
package com.baseerah.application.infrastructure.persistence;
