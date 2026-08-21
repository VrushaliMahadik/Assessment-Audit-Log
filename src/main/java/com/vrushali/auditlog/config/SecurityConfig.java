package com.vrushali.auditlog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // stateless REST API — no session, so no CSRF exposure
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // SERVICE and ADMIN may write audit events (API-CONTRACT §4)
                .requestMatchers(HttpMethod.POST, "/api/v1/audit/events")
                    .hasAnyAuthority("SERVICE", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/audit/client-account-access")
                    .hasAnyAuthority("SERVICE", "ADMIN")
                // AUDITOR and ADMIN may read audit events (API-CONTRACT §5, §6)
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/events", "/api/v1/audit/events/**")
                    .hasAnyAuthority("AUDITOR", "ADMIN")
                // ADMIN only may verify the hash chain (API-CONTRACT §7, SEC-003)
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/verify")
                    .hasAuthority("ADMIN")
                // Scenario B — ADMIN only (SEC-004, SEC-005)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/audit/events/{id}/redact")
                    .hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/audit/export")
                    .hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/retention/run")
                    .hasAuthority("ADMIN")
                // Remaining requests require authentication; per-endpoint rules added as endpoints are built
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                // 401 — missing or invalid bearer token; WWW-Authenticate: Bearer header only, no body
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                // 403 — authenticated but insufficient authority; no internal detail in response
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Authority names read from 'roles' claim — no ROLE_ prefix; use hasAuthority("ADMIN") not hasRole()
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return jwtConverter;
    }
}

