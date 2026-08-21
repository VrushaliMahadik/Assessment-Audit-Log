package com.vrushali.auditlog.dto;

import java.util.List;

public class RedactAuditEventRequest {
    private List<String> fields;

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
