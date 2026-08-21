package com.vrushali.auditlog.security;

import com.vrushali.auditlog.dto.AuditEventPageResponse;
import com.vrushali.auditlog.dto.AuditEventResponse;
import com.vrushali.auditlog.service.AuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies role-based authorization boundaries — no business logic is tested here.
 * 404 on allowed requests = auth + authz passed; endpoint not yet implemented.
 * 403 on denied requests = correct role enforcement.
 * Authority convention: exact strings (ADMIN, AUDITOR, SERVICE) read from JWT 'roles' claim.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtDecoder jwtDecoder;

    @MockBean
    AuditEventService auditEventService;

    @BeforeEach
    void setupServiceMocks() {
        given(auditEventService.queryEvents(any()))
            .willReturn(new AuditEventPageResponse(Collections.emptyList(), 0, 20, 0L, 0));
    }

    // Test 1 — no JWT → 401
    @Test
    void noJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{}"))
               .andExpect(status().isUnauthorized());
    }

    // Test 2 — invalid JWT → 401
    @Test
    void invalidJwt_returns401() throws Exception {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("bad"));
        mockMvc.perform(post("/api/v1/audit/events")
               .header("Authorization", "Bearer bad-token")
               .contentType(MediaType.APPLICATION_JSON)
               .content("{}"))
               .andExpect(status().isUnauthorized());
    }

    // Test 3 — ADMIN allowed (400 = authz passed; {} body fails validation before service is called)
    @Test
    void adminJwt_allowedToCreateEvent() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{}"))
               .andExpect(status().isBadRequest());
    }

    // Test 4 — AUDITOR allowed to query events (200 OK: endpoint now implemented)
    @Test
    void auditorJwt_allowedToQueryEvents() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events")
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR"))))
               .andExpect(status().isOk());
    }

    // Test 5 — SERVICE allowed (400 = authz passed; {} body fails validation before service is called)
    @Test
    void serviceJwt_allowedToCreateEvent() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .with(jwt().authorities(new SimpleGrantedAuthority("SERVICE")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{}"))
               .andExpect(status().isBadRequest());
    }

    // Test 6 — authenticated but insufficient authority → 403
    @Test
    void adminJwt_allowedToExportEvents() throws Exception {
        given(auditEventService.exportEvents(any(), any())).willReturn(Map.of("recordCount", 0, "records", java.util.Collections.emptyList()));

        mockMvc.perform(get("/api/v1/audit/export?resourceId=resource-1")
               .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
               .andExpect(status().isOk());
    }

    @Test
    void adminJwt_allowedToRedactEvent() throws Exception {
        given(auditEventService.redactEvent(any(), any())).willReturn(new AuditEventResponse());

        mockMvc.perform(patch("/api/v1/audit/events/{id}/redact", UUID.randomUUID())
               .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"fields\":[\"email\"]}"))
               .andExpect(status().isOk());
    }

    @Test
    void adminJwt_emptyRedactionFields_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/audit/events/{id}/redact", UUID.randomUUID())
               .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"fields\":[]}"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void serviceJwt_allowedToRecordClientAccountAccess() throws Exception {
        given(auditEventService.recordClientAccountAccess(any())).willReturn(new AuditEventResponse());

        mockMvc.perform(post("/api/v1/audit/client-account-access")
               .with(jwt().authorities(new SimpleGrantedAuthority("SERVICE")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"actorId\":\"service-1\",\"resourceId\":\"account-1\",\"accessType\":\"READ\"}"))
               .andExpect(status().isCreated());
    }

    @Test
    void auditorJwt_deniedClientAccountAccessWrite() throws Exception {
        mockMvc.perform(post("/api/v1/audit/client-account-access")
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"actorId\":\"auditor-1\",\"resourceId\":\"account-1\",\"accessType\":\"READ\"}"))
               .andExpect(status().isForbidden());
    }

    @Test
    void auditorJwt_deniedScenarioBExport() throws Exception {
        mockMvc.perform(get("/api/v1/audit/export?resourceId=resource-1")
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR"))))
               .andExpect(status().isForbidden());
    }

    @Test
    void auditorJwt_deniedScenarioBRedaction() throws Exception {
        mockMvc.perform(patch("/api/v1/audit/events/{id}/redact", UUID.randomUUID())
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"fields\":[\"email\"]}"))
               .andExpect(status().isForbidden());
    }

    @Test
    void auditorJwt_deniedScenarioBRetentionTrigger() throws Exception {
        mockMvc.perform(post("/api/v1/admin/retention/run")
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR"))))
               .andExpect(status().isForbidden());
    }

    @Test
    void auditorJwt_forbiddenOnAdminVerifyEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/audit/verify")
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR"))))
               .andExpect(status().isForbidden());
    }

    @Test
    void serviceJwt_forbiddenOnReadEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events")
               .with(jwt().authorities(new SimpleGrantedAuthority("SERVICE"))))
               .andExpect(status().isForbidden());
    }

    @Test
    void auditorJwt_forbiddenOnCreateEvent() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR")))
               .contentType(MediaType.APPLICATION_JSON)
               .content("{}"))
               .andExpect(status().isForbidden());
    }

    // Test 7 — Step 8 authentication behavior still intact
    @Test
    void unauthenticatedRequest_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events"))
               .andExpect(status().isUnauthorized());
    }
}
