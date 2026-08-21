package com.vrushali.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vrushali.auditlog.model.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public class AuditEventResponse {

    private UUID id;
    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private Object payload;
    private Instant timestamp;
    private String contentHash;
    private String previousHash;
    private Long sequenceNumber;
    private boolean isRedacted;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AuditEventResponse from(AuditEvent event) {
        AuditEventResponse r = new AuditEventResponse();
        r.id = event.getId();
        r.eventType = event.getEventType();
        r.actorId = event.getActorId();
        r.resourceType = event.getResourceType();
        r.resourceId = event.getResourceId();
        r.timestamp = event.getTimestamp();
        r.contentHash = event.getContentHash();
        r.previousHash = event.getPreviousHash();
        r.sequenceNumber = event.getSequenceNumber();
        r.isRedacted = event.isRedacted();
        if (event.getPayload() != null) {
            try {
                r.payload = MAPPER.readValue(event.getPayload(), JsonNode.class);
            } catch (Exception e) {
                r.payload = event.getPayload();
            }
        }
        return r;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getActorId() { return actorId; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public Object getPayload() { return payload; }
    public Instant getTimestamp() { return timestamp; }
    public String getContentHash() { return contentHash; }
    public String getPreviousHash() { return previousHash; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public boolean isRedacted() { return isRedacted; }
}
