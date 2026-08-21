package com.vrushali.auditlog.dto;

import java.util.List;

public class AuditEventPageResponse {

    private List<AuditEventResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public AuditEventPageResponse() {}

    public AuditEventPageResponse(List<AuditEventResponse> content, int page, int size,
                                   long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<AuditEventResponse> getContent() { return content; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}
