package com.vrushali.auditlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrushali.auditlog.service.HashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario C integration tests against PostgreSQL. Requires Docker and skips
 * automatically when Docker is unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "spring.flyway.enabled=true")
class ScenarioCIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @MockBean JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM audit_event");
    }

    @Test
    void successfulReadAccess_isRecordedAsClientAccountEvent() throws Exception {
        mockMvc.perform(post("/api/v1/audit/client-account-access")
                .with(jwt().authorities(new SimpleGrantedAuthority("SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actorId":"service-1","resourceId":"account-1","accessType":"READ", "payload":{"source":"api"}}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventType").value("CLIENT_ACCOUNT_ACCESS"))
            .andExpect(jsonPath("$.resourceType").value("CLIENT_ACCOUNT"))
            .andExpect(jsonPath("$.resourceId").value("account-1"))
            .andExpect(jsonPath("$.payload.accessType").value("READ"))
            .andExpect(jsonPath("$.payload.outcome").value("SUCCESS"))
            .andExpect(jsonPath("$.previousHash").value(HashService.GENESIS));
    }

    @Test
    void successfulWriteAccess_isRecordedAndQueryable() throws Exception {
        mockMvc.perform(post("/api/v1/audit/client-account-access")
                .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actorId":"admin-1","resourceId":"account-2","accessType":"WRITE"}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit/events?resourceType=CLIENT_ACCOUNT&eventType=CLIENT_ACCOUNT_ACCESS")
                .with(jwt().authorities(new SimpleGrantedAuthority("AUDITOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].payload.accessType").value("WRITE"));
    }

    @Test
    void missingAccessType_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/audit/client-account-access")
                .with(jwt().authorities(new SimpleGrantedAuthority("SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":\"service-1\",\"resourceId\":\"account-1\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedAccessType_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/audit/client-account-access")
                .with(jwt().authorities(new SimpleGrantedAuthority("SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actorId":"service-1","resourceId":"account-1","accessType":"DELETE"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedAccess_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/audit/client-account-access")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actorId":"service-1","resourceId":"account-1","accessType":"READ"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void clientAccountAccess_isIncludedInChainVerification() throws Exception {
        mockMvc.perform(post("/api/v1/audit/client-account-access")
                .with(jwt().authorities(new SimpleGrantedAuthority("SERVICE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"actorId":"service-1","resourceId":"account-1","accessType":"READ"}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit/verify")
                .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.checkedRecords").value(1));
    }
}
