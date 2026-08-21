package com.vrushali.auditlog.exception;

public class ExportLimitExceededException extends RuntimeException {

    public ExportLimitExceededException(int maxRecords) {
        super("Export exceeds the maximum allowed record count of " + maxRecords);
    }
}
