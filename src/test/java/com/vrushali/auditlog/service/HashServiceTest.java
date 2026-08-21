package com.vrushali.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrushali.auditlog.model.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HashService — no database or Docker required.
 */
class HashServiceTest {

    HashService hashService;

    @BeforeEach
    void setup() {
        hashService = new HashService(new ObjectMapper());
    }

    @Test
    void genesis_is64ZeroCharacters() {
        assertThat(HashService.GENESIS)
            .hasSize(64)
            .matches("[0]+");
    }

    @Test
    void computeContentHash_produces64CharHexString() {
        String hash = hashService.computeContentHash(sampleEvent());
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void computeContentHash_isDeterministic() {
        AuditEvent event = sampleEvent();
        String hash1 = hashService.computeContentHash(event);
        String hash2 = hashService.computeContentHash(event);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void computeContentHash_differentFields_differentHash() {
        AuditEvent a = sampleEvent();
        AuditEvent b = sampleEvent();
        b.setActorId("different-actor");
        assertThat(hashService.computeContentHash(a))
            .isNotEqualTo(hashService.computeContentHash(b));
    }

    @Test
    void computeContentHash_nullPayload_isConsistent() {
        AuditEvent a = sampleEvent();
        a.setPayload(null);
        String h1 = hashService.computeContentHash(a);
        String h2 = hashService.computeContentHash(a);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void computeContentHash_nullPayload_differsFromNonNull() {
        AuditEvent withNull = sampleEvent();
        withNull.setPayload(null);

        AuditEvent withPayload = sampleEvent();
        withPayload.setPayload("{\"key\":\"value\"}");

        assertThat(hashService.computeContentHash(withNull))
            .isNotEqualTo(hashService.computeContentHash(withPayload));
    }

    @Test
    void normalizePayload_isIdempotent() throws Exception {
        String raw = "{\"z\":1,\"a\":2}";
        String once = hashService.normalizePayload(raw);
        String twice = hashService.normalizePayload(once);
        assertThat(once).isEqualTo(twice);
    }

    @Test
    void normalizePayload_sortsKeys() {
        String normalized = hashService.normalizePayload("{\"z\":1,\"a\":2}");
        assertThat(normalized).startsWith("{\"a\"");
    }

    @Test
    void buildCanonical_containsAllExpectedFields() throws Exception {
        AuditEvent event = sampleEvent();
        String canonical = hashService.buildCanonical(event);
        assertThat(canonical)
            .contains("\"actorId\"")
            .contains("\"eventType\"")
            .contains("\"payload\"")
            .contains("\"resourceId\"")
            .contains("\"resourceType\"")
            .contains("\"timestamp\"");
    }

    private AuditEvent sampleEvent() {
        AuditEvent e = new AuditEvent();
        e.setId(UUID.randomUUID());
        e.setEventType("USER_LOGIN");
        e.setActorId("user-123");
        e.setResourceType("USER_ACCOUNT");
        e.setResourceId("acc-456");
        e.setPayload("{\"ip\":\"10.0.0.1\"}");
        e.setTimestamp(Instant.parse("2026-08-21T10:00:00Z").truncatedTo(ChronoUnit.MICROS));
        e.setContentHash("placeholder");
        e.setPreviousHash(HashService.GENESIS);
        return e;
    }
}
