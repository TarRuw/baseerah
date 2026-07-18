/**
 * Gateway infrastructure (part of the application layer). Implementations of the swappable engines — the
 * forecast and GenAI clients — live here, selected by configuration.
 *
 * <p>Same layer as the application services that use them; they are same-layer collaborators, not outbound
 * ports. Filled by the forecast (10.3) and genai (10.8) slices.
 */
package com.baseerah.application.infrastructure.gateway;
