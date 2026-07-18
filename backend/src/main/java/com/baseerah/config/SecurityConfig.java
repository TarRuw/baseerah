package com.baseerah.config;

import com.baseerah.api.support.OwnershipGuard;
import com.baseerah.application.infrastructure.security.JwtService;
import com.baseerah.shared.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The real stateless JWT security chain for Phase 9 (Step 9.2), replacing the Step 9.1 permit-all bridge.
 *
 * <p>Every request passes through {@link JwtAuthFilter}, which authenticates a valid
 * {@code Authorization: Bearer} token. Public paths (health + {@code /auth/otp/**} + CORS preflight) are
 * permitted; everything else under {@code /api/v1/**} requires authentication. Step 9.3 adds the role
 * gate here ({@code /bank/**} → {@code ROLE_BANK}, the consumer API → {@code ROLE_CONSUMER}); the
 * finer-grained per-client ownership check lives in {@link OwnershipGuard}. Authentication (401) and
 * authorization (403) failures are rendered as the shared {@link ApiErrorResponse} envelope rather than
 * Spring's default HTML/JSON, so the Flutter client always parses one shape.
 *
 * <p>{@code cors(withDefaults())} reuses the MVC CORS config from {@code WebCorsConfig} (via the MVC
 * handler-mapping introspector), so the Flutter web dev origin keeps working through the security chain.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/api/v1/health",
        "/api/v1/auth/otp/**"
    };

    // Only in a servlet web context: the non-web (@SpringBootTest webEnvironment=NONE) slices have no
    // HttpSecurity to inject and need no filter chain, so this bean must not be attempted there.
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
            AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Bank Portal is bank-officer-only; the consumer API (persona list + every
                        // /clients/{id}/... route) is consumer-only. Per-row ownership within the
                        // consumer API is enforced by OwnershipGuard (Step 9.3); this is the role gate.
                        .requestMatchers("/api/v1/bank/**").hasRole("BANK")
                        .requestMatchers("/api/v1/clients", "/api/v1/clients/**").hasRole("CONSUMER")
                        // Reference data (Phase 11): the declared-expense category picker vocabulary is the
                        // same for everyone and belongs to no client — so it is role-agnostic, any valid
                        // token, either role. This is an *intentional* authenticated() matcher (not an
                        // OwnershipGuard route): the picker is consumed by consumers but must not fall
                        // through to the CONSUMER role gate, and there is no prior role-agnostic
                        // reference-data GET to inherit from.
                        .requestMatchers("/api/v1/categories/**").authenticated()
                        // /api/v1/auth/me (and anything else) merely requires a valid token.
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService);
    }

    /** 401 for missing/invalid credentials, rendered as the shared error envelope. */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeEnvelope(objectMapper, response,
                HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication is required to access this resource.");
    }

    /** 403 for an authenticated caller lacking the required authority, as the shared error envelope. */
    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeEnvelope(objectMapper, response,
                HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "You do not have permission to access this resource.");
    }

    private static void writeEnvelope(ObjectMapper objectMapper, HttpServletResponse response,
            int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message));
    }
}
