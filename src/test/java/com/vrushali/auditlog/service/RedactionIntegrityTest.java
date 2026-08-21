package com.vrushali.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrushali.auditlog.dto.VerifyChainResponse;
import com.vrushali.auditlog.model.AuditEvent;
import com.vrushali.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedactionIntegrityTest {

    @Mock
    private AuditEventRepository repository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private HashService hashService;
    private AuditEventService service;

    @BeforeEach
    void setUp() {
        hashService = new HashService(new ObjectMapper());
        service = new AuditEventService(repository, hashService, jdbcTemplate,
            new ObjectMapper(), 30L);
    }

    @Test
    void redaction_keepsChainValid() throws Exception {
        AuditEvent event = event("EVENT", "{\"ssn\":\"123\",\"name\":\"Alice\"}",
            HashService.GENESIS);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(repository.findAllForVerification()).thenReturn(List.of(event));

        service.redactEvent(event.getId(), List.of("ssn"));

        VerifyChainResponse result = service.verifyChain();

        assertThat(result.isValid()).isTrue();
        assertThat(event.getPayload()).contains("[REDACTED]");
        assertThat(event.getOriginalPayload()).contains("123");
    }

    @Test
    void tamperingWithNonRedactedPayload_isDetected() {
        AuditEvent event = event("EVENT", "{\"value\":\"original\"}",
            HashService.GENESIS);
        event.setPayload("{\"value\":\"tampered\"}");
        when(repository.findAllForVerification()).thenReturn(List.of(event));

        VerifyChainResponse result = service.verifyChain();

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFirstInconsistentRecord().getViolationType())
            .isEqualTo("CONTENT_HASH_MISMATCH");
    }

    @Test
    void tamperingWithRedactedPayload_isDetected() throws Exception {
        AuditEvent event = event("EVENT", "{\"secret\":\"original\"}",
            HashService.GENESIS);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(repository.findAllForVerification()).thenReturn(List.of(event));

        service.redactEvent(event.getId(), List.of("secret"));
        event.setPayload("{\"secret\":\"tampered\"}");

        VerifyChainResponse result = service.verifyChain();

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFirstInconsistentRecord().getViolationType())
            .isEqualTo("REDACTED_CONTENT_HASH_MISMATCH");
    }

    @Test
    void redactingMiddleEvent_keepsEntireChainValid() throws Exception {
        AuditEvent first = event("FIRST", "{\"value\":1}", HashService.GENESIS);
        AuditEvent middle = event("MIDDLE", "{\"secret\":\"value\"}", first.getContentHash());
        AuditEvent last = event("LAST", "{\"value\":3}", middle.getContentHash());
        when(repository.findById(middle.getId())).thenReturn(Optional.of(middle));
        when(repository.findAllForVerification()).thenReturn(List.of(first, middle, last));

        service.redactEvent(middle.getId(), List.of("secret"));

        VerifyChainResponse result = service.verifyChain();

        assertThat(result.isValid()).isTrue();
        assertThat(last.getPreviousHash()).isEqualTo(middle.getContentHash());
    }

    private AuditEvent event(String eventType, String payload, String previousHash) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setActorId("actor");
        event.setResourceType("RESOURCE");
        event.setResourceId(eventType.toLowerCase());
        event.setPayload(payload);
        event.setOriginalPayload(payload);
        event.setTimestamp(Instant.parse("2026-08-21T10:00:00Z"));
        event.setPreviousHash(previousHash);
        event.setContentHash(hashService.computeContentHash(event));
        return event;
    }
}