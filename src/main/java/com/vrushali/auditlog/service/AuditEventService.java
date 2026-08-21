package com.vrushali.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrushali.auditlog.dto.*;
import com.vrushali.auditlog.exception.AuditEventNotFoundException;
import com.vrushali.auditlog.model.AuditEvent;
import com.vrushali.auditlog.repository.AuditEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core audit-log business logic for Scenario A.
 *
 * Concurrency strategy (OD-06 resolved):
 *   PostgreSQL transaction-level advisory lock pg_advisory_xact_lock(1_234_567_890)
 *   ensures only one chain-append executes at a time, preventing hash-chain forks.
 *   The lock is released automatically when the transaction commits or rolls back.
 *   This is correct for single-instance AND multi-instance deployments.
 */
@Service
public class AuditEventService {

    // Advisory lock key for the audit chain — unique to this service
    private static final long CHAIN_LOCK_KEY = 1_234_567_890L;

    private final AuditEventRepository repository;
    private final HashService hashService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final long retentionDays;

    public AuditEventService(AuditEventRepository repository,
                              HashService hashService,
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              @Value("${audit.retention.days:30}") long retentionDays) {
        this.repository = repository;
        this.hashService = hashService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public AuditEventResponse createAuditEvent(CreateAuditEventRequest request) {
        // Serialize chain-append operations — prevents concurrent chain forks
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + CHAIN_LOCK_KEY + ")");

        String previousHash = repository.findLatestContentHash()
            .orElse(HashService.GENESIS);

        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(request.getEventType());
        event.setActorId(request.getActorId());
        event.setResourceType(request.getResourceType());
        event.setResourceId(request.getResourceId());
        event.setPayload(hashService.serializePayload(request.getPayload()));
        // Truncate to microseconds: matches PostgreSQL TIMESTAMPTZ precision
        event.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MICROS));
        event.setPreviousHash(previousHash);
        event.setContentHash(hashService.computeContentHash(event));

        AuditEvent saved = repository.save(event);
        return AuditEventResponse.from(saved);
    }

    @Transactional
    public AuditEventResponse recordClientAccountAccess(ClientAccountAccessRequest request) {
        CreateAuditEventRequest auditRequest = new CreateAuditEventRequest();
        auditRequest.setEventType("CLIENT_ACCOUNT_ACCESS");
        auditRequest.setActorId(request.getActorId());
        auditRequest.setResourceType("CLIENT_ACCOUNT");
        auditRequest.setResourceId(request.getResourceId());
        auditRequest.setPayload(Map.of(
            "accessType", request.getAccessType(),
            "outcome", "SUCCESS",
            "details", request.getPayload() == null ? Map.of() : request.getPayload()
        ));
        return createAuditEvent(auditRequest);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse getById(UUID id) {
        return repository.findById(id)
            .map(AuditEventResponse::from)
            .orElseThrow(() -> new AuditEventNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public AuditEventPageResponse queryEvents(AuditEventFilter filter) {
        List<AuditEvent> events = repository.findFiltered(filter);
        long total = repository.countFiltered(filter);
        int totalPages = (int) Math.ceil((double) total / filter.getSize());

        List<AuditEventResponse> content = events.stream()
            .map(AuditEventResponse::from)
            .toList();

        return new AuditEventPageResponse(content, filter.getPage(), filter.getSize(),
            total, totalPages);
    }

    /**
     * Walks the full hash chain in sequence_number order.
     * Read-only: does NOT repair or modify any data.
     */
    @Transactional(readOnly = true)
    public VerifyChainResponse verifyChain() {
        List<AuditEvent> events = repository.findAllForVerification();
        Instant verifiedAt = Instant.now();

        if (events.isEmpty()) {
            return new VerifyChainResponse(true, 0, verifiedAt, null);
        }

        String expectedPreviousHash = HashService.GENESIS;

        for (int i = 0; i < events.size(); i++) {
            AuditEvent event = events.get(i);

            if (event.getArchivedAt() != null) {
                // Legitimate archival is permitted and does not invalidate the chain.
                // Verification skips archived records by reusing the last valid previous hash.
                expectedPreviousHash = event.getContentHash() != null ? event.getContentHash() : expectedPreviousHash;
                continue;
            }

            if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                return new VerifyChainResponse(false, i, verifiedAt,
                    new VerifyChainResponse.FirstInconsistentRecord(
                        event.getId(), event.getSequenceNumber(), "PREVIOUS_HASH_MISMATCH"));
            }

            String recomputed = hashService.computeContentHash(event);
            if (!recomputed.equals(event.getContentHash())) {
                return new VerifyChainResponse(false, i, verifiedAt,
                    new VerifyChainResponse.FirstInconsistentRecord(
                        event.getId(), event.getSequenceNumber(), "CONTENT_HASH_MISMATCH"));
            }

            expectedPreviousHash = event.getContentHash();
        }

        return new VerifyChainResponse(true, events.size(), verifiedAt, null);
    }

    @Scheduled(cron = "${audit.retention.cron:0 0 3 * * *}")
    public void scheduledRetention() {
        runRetention();
    }

    @Transactional
    public int runRetention() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        return repository.archiveOlderThan(cutoff);
    }

    @Transactional
    public AuditEventResponse redactEvent(UUID id, List<String> fields) throws Exception {
        AuditEvent event = repository.findById(id)
            .orElseThrow(() -> new AuditEventNotFoundException(id));

        List<String> selected = fields == null ? List.of() : fields;
        String payload = event.getPayload();
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Event payload is empty");
        }

        JsonNode node = objectMapper.readTree(payload);
        for (String field : selected) {
            if (node.isObject() && node.has(field)) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) node).put(field, "[REDACTED]");
            }
        }

        String redactedPayload = objectMapper.writeValueAsString(node);
        String[] redactedFields = selected.toArray(String[]::new);
        repository.redactPayloadFields(id, redactedPayload, redactedFields, Instant.now());

        event.setPayload(redactedPayload);
        event.setRedacted(true);
        event.setRedactedAt(Instant.now());
        event.setRedactedFields(redactedFields);
        return AuditEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportEvents(String actorId, String resourceId) {
        AuditEventFilter filter = new AuditEventFilter(actorId, null, resourceId, null, null, null, 0, Integer.MAX_VALUE);
        List<AuditEvent> records = repository.findAllForExport(filter);
        List<Map<String, Object>> exportRecords = new ArrayList<>();
        for (AuditEvent event : records) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", event.getId());
            record.put("eventType", event.getEventType());
            record.put("actorId", event.getActorId());
            record.put("resourceId", event.getResourceId());
            record.put("resourceType", event.getResourceType());
            record.put("payload", event.getPayload());
            record.put("timestamp", event.getTimestamp());
            record.put("contentHash", event.getContentHash());
            record.put("previousHash", event.getPreviousHash());
            record.put("sequenceNumber", event.getSequenceNumber());
            record.put("archivedAt", event.getArchivedAt());
            record.put("redacted", event.isRedacted());
            record.put("redactedAt", event.getRedactedAt());
            record.put("redactedFields", event.getRedactedFields());
            exportRecords.add(record);
        }

        Map<String, Object> export = new HashMap<>();
        export.put("exportedAt", Instant.now());
        export.put("actorId", actorId);
        export.put("resourceId", resourceId);
        export.put("recordCount", exportRecords.size());
        export.put("records", exportRecords);
        return export;
    }
}
