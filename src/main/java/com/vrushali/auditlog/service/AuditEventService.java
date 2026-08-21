package com.vrushali.auditlog.service;

import com.vrushali.auditlog.dto.*;
import com.vrushali.auditlog.exception.AuditEventNotFoundException;
import com.vrushali.auditlog.model.AuditEvent;
import com.vrushali.auditlog.repository.AuditEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    public AuditEventService(AuditEventRepository repository,
                              HashService hashService,
                              JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.hashService = hashService;
        this.jdbcTemplate = jdbcTemplate;
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

            // Verify the previous-hash link
            if (!expectedPreviousHash.equals(event.getPreviousHash())) {
                return new VerifyChainResponse(false, i, verifiedAt,
                    new VerifyChainResponse.FirstInconsistentRecord(
                        event.getId(), event.getSequenceNumber(), "PREVIOUS_HASH_MISMATCH"));
            }

            // Recompute and verify the content hash
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
}
