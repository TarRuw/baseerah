/**
 * Domain layer (innermost). Pure Java — no Spring, JPA, or Jackson. Holds the rule-bearing algorithms
 * (calculators, rules), value objects, result records, and enums, grouped by feature.
 *
 * <p>Dependency rule: {@code api → application → domain}. Domain depends on nothing but the JDK and
 * {@code domain.kernel}; it imports no framework. Filled by the feature slices (steps 10.2+); enforced by
 * ArchUnit in step 10.11.
 */
package com.baseerah.domain;
