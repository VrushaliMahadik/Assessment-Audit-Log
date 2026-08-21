-- V1: Initial audit_event table
--
-- Resolved design decisions (previously OPEN in docs/DATABASE-DESIGN.md):
--   DB-05 / OD-15: Primary key → UUID (application-generated; no sequential count exposure)
--   DB-10 / OD-05: Chain ordering → sequence_number BIGSERIAL (unambiguous monotonic ordering;
--                  eliminates timestamp-collision risk on concurrent writes)
--
-- Deferred to future migrations: Scenario B columns (is_redacted, archived_at, redacted_fields)

CREATE TABLE audit_event (
    id              UUID         NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    actor_id        VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(255) NOT NULL,
    resource_id     VARCHAR(255) NOT NULL,
    payload         JSONB,
    timestamp       TIMESTAMPTZ  NOT NULL,
    content_hash    VARCHAR(128) NOT NULL,
    previous_hash   VARCHAR(128) NOT NULL,
    -- BIGSERIAL provides the authoritative chain-ordering sequence (DB-10 resolved)
    sequence_number BIGSERIAL    NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_event          PRIMARY KEY (id),
    -- UNIQUE on sequence_number also serves as the chain-ordering index (idx_ae_sequence_number)
    CONSTRAINT uq_audit_event_sequence UNIQUE (sequence_number)
    -- content_hash UNIQUE constraint intentionally omitted:
    --   identical events produce identical hashes; constraint would reject legitimate duplicates
);

-- Indexes for query patterns defined in docs/DATABASE-DESIGN.md §10
CREATE INDEX idx_ae_actor_id            ON audit_event (actor_id);
CREATE INDEX idx_ae_resource_type       ON audit_event (resource_type);
CREATE INDEX idx_ae_resource_id         ON audit_event (resource_id);
CREATE INDEX idx_ae_event_type          ON audit_event (event_type);
CREATE INDEX idx_ae_timestamp           ON audit_event (timestamp);
CREATE INDEX idx_ae_actor_timestamp     ON audit_event (actor_id,      timestamp);
CREATE INDEX idx_ae_resource_timestamp  ON audit_event (resource_type, resource_id, timestamp);
