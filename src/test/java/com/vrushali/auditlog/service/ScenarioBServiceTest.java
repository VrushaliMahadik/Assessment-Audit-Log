package com.vrushali.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrushali.auditlog.model.AuditEvent;
import com.vrushali.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ScenarioBServiceTest {

    @Mock
    private AuditEventRepository repository;

    @Mock
    private HashService hashService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AuditEventService service;

    @BeforeEach
    void setUp() {
        service = new AuditEventService(repository, hashService, jdbcTemplate, new ObjectMapper(), 30L);
    }

    @Test
    void redactEvent_replacesConfiguredFieldsAndMarksRedacted() throws Exception {
        UUID id = UUID.randomUUID();
        AuditEvent event = new AuditEvent();
        event.setId(id);
        event.setPayload("{\"ssn\":\"123\",\"name\":\"Alice\"}");

        when(repository.findById(id)).thenReturn(Optional.of(event));

        service.redactEvent(id, List.of("ssn"));

        verify(repository).redactPayloadFields(eq(id), anyString(), any(),
            eq(new String[] {"ssn"}), any(Instant.class));
    }

    @Test
    void runRetention_archivesExpiredRecords() {
        when(repository.archiveOlderThan(any(Instant.class))).thenReturn(3);

        int archived = service.runRetention();

        assertThat(archived).isEqualTo(3);
    }

    @Test
    void exportEvents_buildsSelfContainedPayload() {
        UUID id = UUID.randomUUID();
        AuditEvent event = new AuditEvent();
        event.setId(id);
        event.setActorId("user-1");
        event.setResourceId("resource-9");
        event.setPayload("{\"name\":\"Alice\"}");
        event.setContentHash("abc");
        event.setPreviousHash(HashService.GENESIS);

        when(repository.findAllForExport(any())).thenReturn(List.of(event));

        Map<String, Object> export = service.exportEvents("user-1", null);

        assertThat(export).containsKey("records");
        assertThat(export.get("records")).isInstanceOf(List.class);
    }

    @Test
    void exportEvents_rejectsResultsOverConfiguredLimit() {
        ReflectionTestUtils.setField(service, "maxExportRecords", 1);
        when(repository.findAllForExport(any())).thenReturn(List.of(new AuditEvent(), new AuditEvent()));

        assertThatThrownBy(() -> service.exportEvents("user-1", null))
            .isInstanceOf(com.vrushali.auditlog.exception.ExportLimitExceededException.class);
    }
}
