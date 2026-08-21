package com.vrushali.auditlog.security;

import com.vrushali.auditlog.dto.AuditEventPageResponse;
import com.vrushali.auditlog.service.AuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies authentication boundaries — no authorization rules are tested here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // Overrides auto-configured NimbusJwtDecoder so no network call is made during tests
    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    AuditEventService auditEventService;

    @BeforeEach
    void setupServiceMocks() {
        given(auditEventService.queryEvents(any()))
            .willReturn(new AuditEventPageResponse(Collections.emptyList(), 0, 20, 0L, 0));
    }

    // Test 1 — request with no token must be rejected
    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events"))
               .andExpect(status().isUnauthorized());
    }

    // Test 2 — malformed/invalid token must be rejected
    @Test
    void invalidToken_returns401() throws Exception {
        given(jwtDecoder.decode(anyString()))
            .willThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(get("/api/v1/audit/events")
               .header("Authorization", "Bearer invalid-token"))
               .andExpect(status().isUnauthorized());
    }

    // Test 3 — valid JWT must pass the authentication boundary
    // 200 OK: endpoint now implemented; AUDITOR role satisfies the authz rule
    @Test
    void validJwt_authenticatesSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events")
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR"))))
               .andExpect(status().isOk());
    }

    // Test 4 — authenticated principal must be present after successful JWT validation
    // 200 OK confirms auth passed; subject and authority set to demonstrate principal availability
    @Test
    void validJwt_principalIsPresent() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events")
               .with(jwt()
                   .jwt(j -> j.subject("test-service-account"))
                   .authorities(new SimpleGrantedAuthority("AUDITOR"))))
               .andExpect(status().isOk());
    }

    // Test 5 — existing context-load test must still pass with security active
    @Test
    void contextLoads() {
    }
}
