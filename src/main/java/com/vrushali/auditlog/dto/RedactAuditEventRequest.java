package com.vrushali.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class RedactAuditEventRequest {
    @NotEmpty
    @Size(max = 50)
    private List<@NotBlank @Size(max = 255) String> fields;

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
