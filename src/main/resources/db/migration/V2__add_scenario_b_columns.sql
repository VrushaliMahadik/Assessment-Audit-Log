-- V2: Add Scenario B columns (retention, redaction)
-- Resolved design decisions:
--   OD-08: Soft-delete via archived_at (no hard delete; chain hashes remain intact)
--   OD-09: Redaction via payload field replacement; original contentHash preserved (documented trade-off)

ALTER TABLE audit_event
    ADD COLUMN archived_at      TIMESTAMPTZ,
    ADD COLUMN is_redacted      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN redacted_at      TIMESTAMPTZ,
    ADD COLUMN redacted_fields  TEXT[];

-- Index supporting efficient retention queries
CREATE INDEX idx_ae_archived_at ON audit_event (archived_at) WHERE archived_at IS NULL;
CREATE INDEX idx_ae_timestamp_archived ON audit_event (timestamp) WHERE archived_at IS NULL;
