package com.vrushali.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ClientAccountAccessRequest {

    @NotBlank
    @Size(max = 255)
    private String actorId;

    @NotBlank
    @Size(max = 255)
    private String resourceId;

    @NotBlank
    @Pattern(regexp = "READ|WRITE")
    private String accessType;

    private Object payload;

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getAccessType() { return accessType; }
    public void setAccessType(String accessType) { this.accessType = accessType; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
