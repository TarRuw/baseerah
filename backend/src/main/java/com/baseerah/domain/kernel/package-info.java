/**
 * Domain kernel. Shared, feature-agnostic domain records used across the rule-bearing algorithms:
 * {@code LedgerEntry}, {@code Money}, {@code Category}, {@code Direction}.
 *
 * <p>Pure Java, framework-free. Populated by the stress pilot (step 10.2), which establishes the canonical
 * slice pattern; reused by every later feature slice.
 */
package com.baseerah.domain.kernel;
