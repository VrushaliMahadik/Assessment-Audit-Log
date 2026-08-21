package com.vrushali.auditlog.model;

import java.time.Instant;
import java.util.UUID;

public class AuditEvent {
    private UUID id;
    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private String payload; // compact JSON string, or null
    private String originalPayload;
    private Instant timestamp;
    private String contentHash;
    private String previousHash;
    private Long sequenceNumber;
    private Instant createdAt;
    private Instant archivedAt;
    private boolean isRedacted;
    private Instant redactedAt;
    private String[] redactedFields;
    private String redactedContentHash;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getOriginalPayload() { return originalPayload; }
    public void setOriginalPayload(String originalPayload) { this.originalPayload = originalPayload; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public boolean isRedacted() { return isRedacted; }
    public void setRedacted(boolean isRedacted) { this.isRedacted = isRedacted; }
    public Instant getRedactedAt() { return redactedAt; }
    public void setRedactedAt(Instant redactedAt) { this.redactedAt = redactedAt; }
    public String[] getRedactedFields() { return redactedFields; }
    public void setRedactedFields(String[] redactedFields) { this.redactedFields = redactedFields; }
    public String getRedactedContentHash() { return redactedContentHash; }
    public void setRedactedContentHash(String redactedContentHash) { this.redactedContentHash = redactedContentHash; }
}
