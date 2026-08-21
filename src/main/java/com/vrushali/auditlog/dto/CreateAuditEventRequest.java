package com.vrushali.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateAuditEventRequest {

    @NotBlank
    @Size(max = 255)
    private String eventType;

    @NotBlank
    @Size(max = 255)
    private String actorId;

    @NotBlank
    @Size(max = 255)
    private String resourceType;

    @NotBlank
    @Size(max = 255)
    private String resourceId;

    private Object payload; // optional structured data; any valid JSON object

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
