package com.vrushali.auditlog.dto;

import java.time.Instant;

public class AuditEventFilter {

    private final String actorId;
    private final String resourceType;
    private final String resourceId;
    private final String eventType;
    private final Instant from;
    private final Instant to;
    private final int page;
    private final int size;

    public AuditEventFilter(String actorId, String resourceType, String resourceId,
                             String eventType, Instant from, Instant to, int page, int size) {
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.eventType = eventType;
        this.from = from;
        this.to = to;
        this.page = page;
        this.size = size;
    }

    public String getActorId() { return actorId; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getEventType() { return eventType; }
    public Instant getFrom() { return from; }
    public Instant getTo() { return to; }
    public int getPage() { return page; }
    public int getSize() { return size; }
}
