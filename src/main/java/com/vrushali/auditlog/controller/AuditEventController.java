package com.vrushali.auditlog.controller;

import com.vrushali.auditlog.dto.*;
import com.vrushali.auditlog.service.AuditEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/audit")
public class AuditEventController {

    private final AuditEventService service;

    public AuditEventController(AuditEventService service) {
        this.service = service;
    }

    @PostMapping("/events")
    public ResponseEntity<AuditEventResponse> createEvent(
        @Valid @RequestBody CreateAuditEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAuditEvent(request));
    }

    @PostMapping("/client-account-access")
    public ResponseEntity<AuditEventResponse> recordClientAccountAccess(
        @Valid @RequestBody ClientAccountAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.recordClientAccountAccess(request));
    }

    @GetMapping("/events")
    public ResponseEntity<AuditEventPageResponse> queryEvents(
        @RequestParam(required = false) String actorId,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) String resourceId,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        AuditEventFilter filter = new AuditEventFilter(actorId, resourceType, resourceId,
            eventType, from, to, page, size);
        return ResponseEntity.ok(service.queryEvents(filter));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<AuditEventResponse> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/verify")
    public ResponseEntity<VerifyChainResponse> verifyChain() {
        return ResponseEntity.ok(service.verifyChain());
    }

    @PatchMapping("/events/{id}/redact")
    public ResponseEntity<AuditEventResponse> redactEvent(
        @PathVariable UUID id,
        @RequestBody RedactAuditEventRequest request) throws Exception {
        return ResponseEntity.ok(service.redactEvent(id, request.getFields()));
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportEvents(
        @RequestParam(required = false) String actorId,
        @RequestParam(required = false) String resourceId) {
        if (actorId == null && resourceId == null) {
            throw new IllegalArgumentException("Either actorId or resourceId is required");
        }
        return ResponseEntity.ok(service.exportEvents(actorId, resourceId));
    }

    @PostMapping("/admin/retention/run")
    public ResponseEntity<Map<String, Object>> runRetention() {
        int archived = service.runRetention();
        Map<String, Object> response = new HashMap<>();
        response.put("archivedRecords", archived);
        response.put("runAt", Instant.now());
        return ResponseEntity.ok(response);
    }
}
