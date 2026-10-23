package com.nexora.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import com.nexora.shared.observability.RequestLoggingFilter;

// The security rules for the whole API.
@Configuration
@EnableMethodSecurity                                   // switches on @PreAuthorize on controller methods
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorHandlers errorHandlers,
                                    @Value("${nexora.management.port:8081}") int managementPort) throws Exception {
        http
                // No cookies/sessions: every request carries its own bearer token, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // public: login/refresh/logout, first-run setup, health probes and the (dev-only) API docs
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/setup/status", "/api/v1/setup/initialize")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // metrics only on the unpublished management port (NFR-OBS-04): elsewhere 401 without a
                        // token and 403 with one, so the public port never serves them
                        .requestMatchers("/actuator/**").access((authentication, context) ->
                                new AuthorizationDecision(context.getRequest().getLocalPort() == managementPort))
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // everything else needs a valid token
                        .anyRequest().authenticated())
                // Validate the "Authorization: Bearer <jwt>" header with the JwtDecoder bean.
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                // who made the request, for the request log line and the logging context (NFR-OBS-01)
                .addFilterAfter(new RequestLoggingFilter.UserCapture(), BearerTokenAuthenticationFilter.class)
                // NFR-SEC-04, 09-SECURITY section 8: Spring's defaults stay (nosniff, frame DENY, no-store); added:
                // no referrer, a CSP that allows nothing (API responses are data, never pages; the dev-only Swagger UI
                // is left out so it still works), and no browser features. No .cors(): no CORS headers at all (NFR-SEC-05).
                .headers(headers -> headers
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                request -> !request.getRequestURI().startsWith("/swagger-ui")
                                        && !request.getRequestURI().startsWith("/v3/api-docs"),
                                new StaticHeadersWriter("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'")))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "geolocation=(), camera=(), microphone=()")));
        return http.build();
    }

    // Turns the "permissions" claim of the token into Spring authorities, without a "SCOPE_" prefix,
    // so hasAuthority('PAYMENT_CREATE') matches directly.
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("permissions");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    // BCrypt through the delegating encoder: hashes are stored as "{bcrypt}$2a$..." so the
    // algorithm can be upgraded later without breaking old hashes.
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
