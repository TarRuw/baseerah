/**
 * Application layer. Use-case {@code @Service} classes (under {@code application.<feature>}) and their
 * infrastructure (under {@code application.infrastructure}) live in this one layer.
 *
 * <p>Dependency rule: {@code api → application → domain}. Depends on {@code domain}; may use Spring and
 * JPA freely. A service calls its repositories and gateways directly — there are no outbound port
 * interfaces; they are same-layer collaborators. Filled by the feature slices (steps 10.2+); enforced by
 * ArchUnit in step 10.11.
 */
package com.baseerah.application;
