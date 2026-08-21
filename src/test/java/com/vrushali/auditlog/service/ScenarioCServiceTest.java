package com.vrushali.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrushali.auditlog.dto.ClientAccountAccessRequest;
import com.vrushali.auditlog.model.AuditEvent;
import com.vrushali.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScenarioCServiceTest {

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
        when(repository.findLatestContentHash()).thenReturn(Optional.of(HashService.GENESIS));
        when(hashService.serializePayload(any())).thenReturn("{\"accessType\":\"READ\",\"outcome\":\"SUCCESS\"}");
        when(hashService.computeContentHash(any())).thenReturn("content-hash");
        when(repository.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordClientAccountAccess_mapsScenarioRulesToExistingAuditChain() {
        ClientAccountAccessRequest request = new ClientAccountAccessRequest();
        request.setActorId("service-1");
        request.setResourceId("account-1");
        request.setAccessType("READ");
        request.setPayload(Map.of("source", "account-service"));

        var response = service.recordClientAccountAccess(request);

        assertThat(response.getEventType()).isEqualTo("CLIENT_ACCOUNT_ACCESS");
        assertThat(response.getActorId()).isEqualTo("service-1");
        assertThat(response.getResourceType()).isEqualTo("CLIENT_ACCOUNT");
        assertThat(response.getResourceId()).isEqualTo("account-1");
        assertThat(response.getPreviousHash()).isEqualTo(HashService.GENESIS);
    }
}
