-- V3: Preserve the original payload and verify redacted representations separately
ALTER TABLE audit_event
    ADD COLUMN original_payload JSONB,
    ADD COLUMN redacted_content_hash VARCHAR(128);

UPDATE audit_event
SET original_payload = payload
WHERE original_payload IS NULL;
