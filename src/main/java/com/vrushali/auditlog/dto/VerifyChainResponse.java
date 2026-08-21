package com.vrushali.auditlog.dto;

import java.time.Instant;
import java.util.UUID;

public class VerifyChainResponse {

    private boolean valid;
    private int checkedRecords;
    private Instant verifiedAt;
    private FirstInconsistentRecord firstInconsistentRecord;

    public VerifyChainResponse() {}

    public VerifyChainResponse(boolean valid, int checkedRecords, Instant verifiedAt,
                                FirstInconsistentRecord firstInconsistentRecord) {
        this.valid = valid;
        this.checkedRecords = checkedRecords;
        this.verifiedAt = verifiedAt;
        this.firstInconsistentRecord = firstInconsistentRecord;
    }

    public boolean isValid() { return valid; }
    public int getCheckedRecords() { return checkedRecords; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public FirstInconsistentRecord getFirstInconsistentRecord() { return firstInconsistentRecord; }

    public static class FirstInconsistentRecord {
        private UUID id;
        private Long sequenceNumber;
        private String violationType;

        public FirstInconsistentRecord() {}

        public FirstInconsistentRecord(UUID id, Long sequenceNumber, String violationType) {
            this.id = id;
            this.sequenceNumber = sequenceNumber;
            this.violationType = violationType;
        }

        public UUID getId() { return id; }
        public Long getSequenceNumber() { return sequenceNumber; }
        public String getViolationType() { return violationType; }
    }
}
