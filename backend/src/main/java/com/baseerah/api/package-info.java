/**
 * API layer (own layer, outermost). Controllers, web request/response DTOs, and web mappers live here.
 *
 * <p>Dependency rule: {@code api → application → domain}. Depends on {@code application}; never the
 * reverse. Imports no {@code jakarta.persistence} (persistence types belong to
 * {@code application.infrastructure.persistence}). Filled by the feature slices (steps 10.2+); enforced by
 * ArchUnit in step 10.11.
 */
package com.baseerah.api;
