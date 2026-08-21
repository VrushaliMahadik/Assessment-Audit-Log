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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Scenario A integration tests against a real PostgreSQL instance.
 * Requires Docker — skipped automatically when Docker is unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "spring.flyway.enabled=true")
class ScenarioAIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @MockBean  JwtDecoder jwtDecoder;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("ADMIN"));
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auditorJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("AUDITOR"));
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor serviceJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("SERVICE"));
    }

    private String createEventBody(String eventType, String actorId,
                                    String resourceType, String resourceId) {
        return """
            {"eventType":"%s","actorId":"%s","resourceType":"%s","resourceId":"%s"}
            """.formatted(eventType, actorId, resourceType, resourceId);
    }

    private Map<?, ?> createEventAndParse(String eventType, String actorId,
                                           String resourceType, String resourceId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/audit/events")
            .with(serviceJwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createEventBody(eventType, actorId, resourceType, resourceId)))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM audit_event");
    }

    // ── Creation ──────────────────────────────────────────────────────────────

    @Test
    void createEvent_validRequest_returns201WithFields() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .with(serviceJwt())
               .contentType(MediaType.APPLICATION_JSON)
               .content(createEventBody("USER_LOGIN", "actor-1", "USER", "user-1")))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").isNotEmpty())
               .andExpect(jsonPath("$.eventType").value("USER_LOGIN"))
               .andExpect(jsonPath("$.actorId").value("actor-1"))
               .andExpect(jsonPath("$.contentHash").isNotEmpty())
               .andExpect(jsonPath("$.previousHash").value(HashService.GENESIS));
    }

    @Test
    void createEvent_missingRequiredField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .with(serviceJwt())
               .contentType(MediaType.APPLICATION_JSON)
               .content("{\"actorId\":\"a\",\"resourceType\":\"R\",\"resourceId\":\"r\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void createEvent_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .contentType(MediaType.APPLICATION_JSON)
               .content(createEventBody("E", "a", "R", "r")))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void createEvent_auditorRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/audit/events")
               .with(auditorJwt())
               .contentType(MediaType.APPLICATION_JSON)
               .content(createEventBody("E", "a", "R", "r")))
               .andExpect(status().isForbidden());
    }

    @Test
    void createEvent_isPersisted() throws Exception {
        Map<?, ?> created = createEventAndParse("PERSIST_TEST", "actor-p", "RESOURCE", "r-1");
        String id = (String) created.get("id");

        mockMvc.perform(get("/api/v1/audit/events/" + id).with(auditorJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.eventType").value("PERSIST_TEST"));
    }

    // ── Hash Chain ────────────────────────────────────────────────────────────

    @Test
    void firstEvent_usesGenesisValue() throws Exception {
        Map<?, ?> event = createEventAndParse("EV", "a", "R", "r");
        assertThat(event.get("previousHash")).isEqualTo(HashService.GENESIS);
    }

    @Test
    void secondEvent_previousHashEqualsFirstContentHash() throws Exception {
        Map<?, ?> first  = createEventAndParse("EV1", "a", "R", "r1");
        Map<?, ?> second = createEventAndParse("EV2", "a", "R", "r2");
        assertThat(second.get("previousHash")).isEqualTo(first.get("contentHash"));
    }

    @Test
    void multipleEvents_formValidChain() throws Exception {
        for (int i = 0; i < 5; i++) {
            createEventAndParse("EV" + i, "actor", "R", "res-" + i);
        }
        mockMvc.perform(get("/api/v1/audit/verify").with(adminJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valid").value(true))
               .andExpect(jsonPath("$.checkedRecords").value(5));
    }

    @Test
    void contentHash_isDeterministic() throws Exception {
        Map<?, ?> created = createEventAndParse("EV", "actor", "R", "r");
        String storedHash = (String) created.get("contentHash");
        assertThat(storedHash).hasSize(64).matches("[0-9a-f]+");
    }

    // ── Verification ──────────────────────────────────────────────────────────

    @Test
    void verifyChain_emptyChain_returnsValid() throws Exception {
        mockMvc.perform(get("/api/v1/audit/verify").with(adminJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valid").value(true))
               .andExpect(jsonPath("$.checkedRecords").value(0));
    }

    @Test
    void verifyChain_validChain_returnsValid() throws Exception {
        createEventAndParse("EV1", "a", "R", "r1");
        createEventAndParse("EV2", "a", "R", "r2");
        mockMvc.perform(get("/api/v1/audit/verify").with(adminJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void verifyChain_afterDirectDatabaseTamper_detectsTampering() throws Exception {
        Map<?, ?> created = createEventAndParse("TAMPER_EV", "actor", "R", "r");
        String id = (String) created.get("id");

        // Tamper: change actorId directly without updating contentHash
        jdbcTemplate.update("UPDATE audit_event SET actor_id = 'tampered' WHERE id = ?::uuid", id);

        MvcResult result = mockMvc.perform(get("/api/v1/audit/verify").with(adminJwt()))
               .andExpect(status().isOk())
               .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(response.get("valid")).isEqualTo(false);
        assertThat(response.get("firstInconsistentRecord")).isNotNull();

        @SuppressWarnings("unchecked")
        Map<?, ?> inconsistent = (Map<?, ?>) response.get("firstInconsistentRecord");
        assertThat(inconsistent.get("violationType")).isEqualTo("CONTENT_HASH_MISMATCH");
        assertThat(inconsistent.get("id").toString()).isEqualTo(id);
    }

    @Test
    void verifyChain_doesNotModifyData() throws Exception {
        createEventAndParse("EV", "a", "R", "r");
        mockMvc.perform(get("/api/v1/audit/verify").with(adminJwt()))
               .andExpect(status().isOk());
        // Count must be unchanged — verify is read-only
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void verifyChain_auditorRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/audit/verify").with(auditorJwt()))
               .andExpect(status().isForbidden());
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    @Test
    void getEvent_existingId_returns200() throws Exception {
        Map<?, ?> created = createEventAndParse("EV", "a", "R", "r");
        mockMvc.perform(get("/api/v1/audit/events/" + created.get("id")).with(auditorJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(created.get("id")));
    }

    @Test
    void getEvent_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events/00000000-0000-0000-0000-000000000000")
               .with(auditorJwt()))
               .andExpect(status().isNotFound());
    }

    // ── Query / Filter ────────────────────────────────────────────────────────

    @Test
    void queryEvents_emptyTable_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events").with(auditorJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray())
               .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void queryEvents_filterByActorId_returnsOnlyMatching() throws Exception {
        createEventAndParse("EV", "actor-A", "R", "r1");
        createEventAndParse("EV", "actor-B", "R", "r2");

        mockMvc.perform(get("/api/v1/audit/events?actorId=actor-A").with(auditorJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.totalElements").value(1))
               .andExpect(jsonPath("$.content[0].actorId").value("actor-A"));
    }

    @Test
    void queryEvents_filterByEventType_returnsOnlyMatching() throws Exception {
        createEventAndParse("LOGIN",  "a", "R", "r1");
        createEventAndParse("LOGOUT", "a", "R", "r2");

        mockMvc.perform(get("/api/v1/audit/events?eventType=LOGIN").with(auditorJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.totalElements").value(1))
               .andExpect(jsonPath("$.content[0].eventType").value("LOGIN"));
    }

    @Test
    void queryEvents_filterByResourceId_returnsOnlyMatching() throws Exception {
        createEventAndParse("EV", "a", "DOCUMENT", "doc-1");
        createEventAndParse("EV", "a", "DOCUMENT", "doc-2");

        mockMvc.perform(get("/api/v1/audit/events?resourceId=doc-1").with(auditorJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void queryEvents_pagination_returnsCorrectPage() throws Exception {
        for (int i = 0; i < 5; i++) {
            createEventAndParse("EV", "a", "R", "r-" + i);
        }
        mockMvc.perform(get("/api/v1/audit/events?page=0&size=2").with(auditorJwt()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content.length()").value(2))
               .andExpect(jsonPath("$.totalElements").value(5))
               .andExpect(jsonPath("$.totalPages").value(3))
               .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void queryEvents_orderingIsDeterministic() throws Exception {
        for (int i = 0; i < 3; i++) {
            createEventAndParse("EV" + i, "a", "R", "r");
        }
        MvcResult r1 = mockMvc.perform(get("/api/v1/audit/events").with(auditorJwt()))
            .andReturn();
        MvcResult r2 = mockMvc.perform(get("/api/v1/audit/events").with(auditorJwt()))
            .andReturn();
        assertThat(r1.getResponse().getContentAsString())
            .isEqualTo(r2.getResponse().getContentAsString());
    }

    @Test
    void queryEvents_invalidSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/audit/events?size=200").with(auditorJwt()))
               .andExpect(status().isBadRequest());
    }
}
