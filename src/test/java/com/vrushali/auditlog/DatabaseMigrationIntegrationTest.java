package com.vrushali.auditlog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Flyway migrations against a real PostgreSQL instance via Testcontainers.
 * Requires Docker — skipped automatically when Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "spring.flyway.enabled=true")
class DatabaseMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void migrationRuns_auditEventTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'audit_event'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void requiredColumns_exist() {
        List<String> columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_name = 'audit_event' ORDER BY column_name",
            String.class);
        assertThat(columns).contains(
            "id", "event_type", "actor_id", "resource_type", "resource_id",
            "payload", "timestamp", "content_hash", "previous_hash",
            "sequence_number", "created_at");
    }

    @Test
    void hashColumns_haveCorrectType() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_name = 'audit_event' " +
            "  AND column_name IN ('content_hash', 'previous_hash') " +
            "  AND data_type = 'character varying' " +
            "  AND character_maximum_length = 128",
            Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void timestampColumn_isTimestampWithTimeZone() {
        String dataType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'audit_event' AND column_name = 'timestamp'",
            String.class);
        assertThat(dataType).isEqualTo("timestamp with time zone");
    }

    @Test
    void payloadColumn_isJsonb() {
        String dataType = jdbcTemplate.queryForObject(
            "SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'audit_event' AND column_name = 'payload'",
            String.class);
        assertThat(dataType).isEqualTo("jsonb");
    }

    @Test
    void primaryKeyConstraint_exists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
            "WHERE table_name = 'audit_event' AND constraint_type = 'PRIMARY KEY'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sequenceUniqueConstraint_exists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.table_constraints " +
            "WHERE table_name = 'audit_event' " +
            "  AND constraint_name = 'uq_audit_event_sequence' " +
            "  AND constraint_type = 'UNIQUE'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void queryIndexes_exist() {
        List<String> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'audit_event'",
            String.class);
        assertThat(indexes).contains(
            "idx_ae_actor_id", "idx_ae_resource_type", "idx_ae_resource_id",
            "idx_ae_event_type", "idx_ae_timestamp",
            "idx_ae_actor_timestamp", "idx_ae_resource_timestamp");
    }

    @Test
    @Transactional
    void canInsertAndReadAuditEvent() {
        jdbcTemplate.update(
            "INSERT INTO audit_event " +
            "  (id, event_type, actor_id, resource_type, resource_id, payload, timestamp, content_hash, previous_hash) " +
            "VALUES " +
            "  (gen_random_uuid(), ?, ?, ?, ?, ?::jsonb, NOW(), ?, ?)",
            "DB_MIGRATION_TEST", "actor-1", "RESOURCE", "res-1",
            "{\"test\": \"migration\"}", "testhash128chars", "genesis");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE event_type = ?",
            Integer.class, "DB_MIGRATION_TEST");
        assertThat(count).isEqualTo(1);
    }
}
