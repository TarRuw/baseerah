package com.baseerah.common;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the Flutter dev origin (DESIGN.md §6 — frontend calls the API cross-origin).
 *
 * <p>Origins are configurable via {@code baseerah.cors.allowed-origin-patterns} (comma-separated)
 * so device/emulator/web hosts can be added without a rebuild; defaults cover Flutter web/dev on
 * localhost. Uses {@code allowedOriginPatterns} (not {@code allowedOrigins}) so {@code localhost:*}
 * wildcard ports are permitted.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOriginPatterns;

    public WebCorsConfig(
            @Value("${baseerah.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept-Language", "Accept", "Authorization")
                .allowCredentials(true);
    }
}
