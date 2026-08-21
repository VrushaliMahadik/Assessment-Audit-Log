# Database Design — Audit Log Service

**Engineer:** Vrushali Mahadik  
**Status:** Proposed — design document only. No database has been created. Implementation follows after review.

---

## 1. Purpose

This document defines the proposed PostgreSQL database design for the Audit Log Service before any database implementation begins.

It serves as the design contract between the architecture phase and the implementation phase. All table structures, field definitions, indexes, constraints, and design decisions are documented here so that Flyway migrations and JPA entities can be written with a clear, reviewed foundation.

**Design history note:** This document records the original database design decisions and alternatives. The implemented PostgreSQL schema is maintained by the Flyway migrations under `src/main/resources/db/migration`; the current implementation uses UUID IDs, a BIGSERIAL `sequence_number`, JSONB payloads, Scenario B retention/redaction columns, and the indexes verified by the database integration tests when Docker is available.

---

## 2. Database Technology

| Component | Choice | Reason |
|-----------|--------|--------|
| Database | **PostgreSQL** | Explicitly required by the assessment |
| Access | Spring Data JPA + Hibernate | Standard Spring Boot persistence stack |
| Migrations | Flyway (proposed) | Versioned, reproducible schema management |
| Runtime | Java 21 | Approved — AI-001 |
| Framework | Spring Boot 3.3.4 | Project baseline |
| Build | Maven | Project baseline |

No additional database technologies are introduced. A single PostgreSQL instance is sufficient for the assessment scope.

---

## 3. Design Goals

Every goal below is supported by an explicit requirement or architectural decision.

| Goal | Source |
|------|--------|
| Durable audit-event storage | NFR-002, assessment |
| Append-only audit history | REQ-A-012 |
| Tamper-evident hash-chain support | REQ-A-024–030 |
| Efficient querying with multiple filters | REQ-A-013–021 |
| Pagination support | REQ-A-022 |
| Deterministic ordering | REQ-A-023 |
| Chain verification support | REQ-A-031–036 |
| Configurable retention support | REQ-B-001–004 |
| Structured redaction support | REQ-B-005–008 |
| Export support | REQ-B-009–013 |
| Transactional consistency on write | REQ-A-030, NFR-002 |

---

## 4. Audit Event Table

**Proposed table name:** `audit_event`

| Column | Purpose | Proposed Type | Nullable | Source |
|--------|---------|---------------|----------|--------|
| `id` | Unique record identifier | `UUID` or `BIGSERIAL` — **PENDING OD-15** | NOT NULL | Engineering |
| `event_type` | Classification of the event | `VARCHAR(255)` | NOT NULL | REQ-A-002 |
| `actor_id` | Identity of the actor | `VARCHAR(255)` | NOT NULL | REQ-A-003 |
| `resource_type` | Type of the affected resource | `VARCHAR(255)` | NOT NULL | REQ-A-004 |
| `resource_id` | Identifier of the affected resource | `VARCHAR(255)` | NOT NULL | REQ-A-005 |
| `payload` | Optional structured event data | `JSONB` | NULL | REQ-A-006 |
| `timestamp` | Logical event time (server-assigned) | `TIMESTAMPTZ` | NOT NULL | REQ-A-007 |
| `content_hash` | Cryptographic hash of auditable fields | `VARCHAR(128)` | NOT NULL | REQ-A-010 |
| `previous_hash` | `content_hash` of the preceding record | `VARCHAR(128)` | NOT NULL | REQ-A-011 |
| `sequence_number` | Monotonic ordering field | `BIGINT` — **PROPOSED, OD-05** | NOT NULL (if adopted) | Engineering — chain ordering |
| `created_at` | Server-controlled DB insertion time | `TIMESTAMPTZ` — **PROPOSED** | NOT NULL (if adopted) | Engineering — audit trail precision |

**Notes on proposed engineering fields:**

- `sequence_number` — proposed to provide unambiguous chain ordering independent of timestamp precision. Required if OD-05 resolves to sequence-based ordering.
- `created_at` — server-set `DEFAULT NOW()` column distinct from the logical `timestamp`. Not an explicit assessment requirement; proposed for operational traceability.

Both proposed fields must be reviewed and approved before schema implementation.

---

## 5. Primary Key and Identifier Strategy

**Options under consideration (OD-15 — PENDING):**

| Option | Type | Pros | Cons |
|--------|------|------|------|
| `UUID` (v4) | Random UUID | No ordering leakage; globally unique | Does not encode sequence; index fragmentation on large tables |
| `UUID` (v7) | Time-ordered UUID | Globally unique; monotonically increasing | Requires UUID v7 generation support |
| `BIGSERIAL` | Auto-increment integer | Simple; naturally ordered; compact index | Sequential — exposes record count |

**Ordering implication:** If `BIGSERIAL` is chosen, it doubles as a natural ordering column. If `UUID` is chosen, a separate `sequence_number` column (or `created_at` with sufficient precision) is needed to define unambiguous chain order.

**PENDING DESIGN DECISION (OD-15).** No primary key type is finalised.

---

## 6. Timestamp Design

**Proposed column:** `timestamp TIMESTAMPTZ NOT NULL`

| Concern | Design Consideration |
|---------|---------------------|
| Timezone | `TIMESTAMPTZ` stores with UTC offset — always normalise to UTC on insert |
| Precision | PostgreSQL `TIMESTAMPTZ` supports microsecond precision |
| Ownership | Server-assigned — the application sets this value, not the client. **PENDING final approval (OD-01)** |
| Canonicalisation | For hash computation, timestamp must be serialised in a fixed canonical format (ISO-8601 UTC, e.g. `2026-08-21T10:00:00.000000Z`) — **OD-03 PENDING** |
| Collision risk | Microsecond timestamps can still collide under high concurrency. A `sequence_number` column (OD-05) eliminates this risk as the authoritative ordering field |

**`created_at` (proposed):** A second `TIMESTAMPTZ DEFAULT NOW()` column set by the database at INSERT time, not overridable by the application. Useful for operational queries independent of business timestamp.

---

## 7. Hash Chain Data Model

The hash chain is encoded directly in the `audit_event` table via two columns:

```
  ┌──────────────────────────────────────────────────────┐
  │  audit_event (first record)                          │
  │  content_hash  = H( auditable fields )               │
  │  previous_hash = '<GENESIS_VALUE>'    ← OD-04 OPEN   │
  └──────────────────────────────────────────────────────┘
              │
              │  previous_hash  =  content_hash of record above
              ▼
  ┌──────────────────────────────────────────────────────┐
  │  audit_event (record N)                              │
  │  content_hash  = H( auditable fields of record N )   │
  │  previous_hash = content_hash of record N-1          │
  └──────────────────────────────────────────────────────┘
              │
              ▼  ...and so on
```

**Field roles:**

| Column | Role |
|--------|------|
| `content_hash` | Hash of this record's own auditable fields. Computed by the application before INSERT. Never updated after insert. |
| `previous_hash` | Hash of the immediately preceding record. Sourced from the previous record's `content_hash`. First record uses the genesis value. |

**Genesis value:** The `previous_hash` of the first record ever inserted. Exact value is **PENDING (OD-04)** — candidates: fixed string `"GENESIS"`, 64 zero characters, or a well-known constant.

**Tamper detection:** If any stored field is modified after INSERT (directly in the database), recomputing `content_hash` from the stored fields will produce a different value. The application's verification walk will detect the mismatch.

**Hash algorithm: PENDING (OD-02)** — SHA-256 or SHA-3-256 under consideration.

---

## 8. Canonicalisation and Hashing

The database stores the output of hashing, not the input. The input (canonical representation) is constructed by the application layer. However, the database design must be consistent with the canonicalisation strategy.

**Fields that participate in `content_hash` computation:**

| Column | Canonicalisation concern |
|--------|--------------------------|
| `event_type` | Plain string — no transformation needed |
| `actor_id` | Plain string — no transformation needed |
| `resource_type` | Plain string — no transformation needed |
| `resource_id` | Plain string — no transformation needed |
| `payload` | Stored as JSONB — **must be serialised deterministically for hashing (OD-03 PENDING)** |
| `timestamp` | Stored as TIMESTAMPTZ — **must be formatted as a fixed canonical string for hashing (OD-03 PENDING)** |

**Fields excluded from `content_hash`:** `id`, `content_hash`, `previous_hash`, `sequence_number`, `created_at` — these are derived or infrastructure fields.

**Null payload:** If `payload` is NULL, the canonical representation must define a consistent null encoding (e.g., empty string, `"null"` literal, or field omission) — **OD-03 PENDING**.

**JSONB storage note:** PostgreSQL JSONB normalises JSON on storage (removes duplicate keys, may reorder). The application must canonicalise the payload to a deterministic string **before** passing it to the hash function — never hash the JSONB column value directly after retrieval without re-canonicalisation.

---

## 9. Append-Only Behaviour

The audit log must be append-only. No UPDATE or DELETE of audit records is permitted through the application API.

**Database-level design considerations:**

| Mechanism | Approach |
|-----------|----------|
| Application enforcement | The service layer exposes no update or delete operations for audit records. This is the primary control. |
| No UPDATE trigger | A PostgreSQL trigger `BEFORE UPDATE ON audit_event` could raise an exception for any update attempt — provides a database-level safety net |
| No DELETE trigger | Similarly, a `BEFORE DELETE ON audit_event` trigger could block accidental deletes |
| Row-level security | PostgreSQL RLS can be configured to deny UPDATE/DELETE for the application database user — preferred for production |
| Privilege separation | The application database user should have INSERT and SELECT only — no UPDATE or DELETE privileges |

**Privileged access risk:** A database administrator with superuser privileges can still modify records directly. This cannot be prevented at the application level. It is detected by the verification endpoint (`GET /audit/verify`). See Section 22 — Risks.

**Triggers and RLS are proposed engineering enhancements.** Their inclusion in the final implementation must be reviewed and approved.

---

## 10. Indexing Strategy

**Proposed indexes:**

| Index | Columns | Type | Justification |
|-------|---------|------|---------------|
| PK | `id` | B-tree (implicit) | Primary key lookup |
| `idx_ae_actor_id` | `actor_id` | B-tree | Filter by actorId (REQ-A-014) |
| `idx_ae_resource_type` | `resource_type` | B-tree | Filter by resourceType (REQ-A-015) |
| `idx_ae_resource_id` | `resource_id` | B-tree | Filter by resourceId (REQ-A-016) |
| `idx_ae_event_type` | `event_type` | B-tree | Filter by eventType (REQ-A-017) |
| `idx_ae_timestamp` | `timestamp` | B-tree | Range filter by from/to (REQ-A-018–019); ordering |
| `idx_ae_sequence_number` | `sequence_number` | B-tree | Chain ordering and verification walk (OD-05, if adopted) |
| `idx_ae_actor_timestamp` | `(actor_id, timestamp)` | Composite B-tree | Combined actorId + time-range filter (REQ-A-020) |
| `idx_ae_resource_timestamp` | `(resource_type, resource_id, timestamp)` | Composite B-tree | Combined resource filter + time-range (REQ-A-020) |

**Index design rationale:**

- Single-column indexes cover individual filter parameters.
- Composite indexes cover the most common combined query patterns.
- The `timestamp` index also supports ORDER BY for deterministic pagination.
- If `sequence_number` is adopted (OD-05), it becomes the authoritative ordering column for verification and pagination.

**Caution:** Excessive indexes increase write overhead. The above set is the minimum justified by the query requirements. Additional composite indexes should only be added after profiling real query patterns.

---

## 11. Query and Pagination Support

The database design supports all required filter combinations through indexed columns and dynamic WHERE clause construction in the application layer.

| Query requirement | Database support |
|-------------------|-----------------|
| Filter by `actor_id` | `WHERE actor_id = ?` — covered by `idx_ae_actor_id` |
| Filter by `resource_type` | `WHERE resource_type = ?` — covered by `idx_ae_resource_type` |
| Filter by `resource_id` | `WHERE resource_id = ?` — covered by `idx_ae_resource_id` |
| Filter by `event_type` | `WHERE event_type = ?` — covered by `idx_ae_event_type` |
| Filter by `from` timestamp | `WHERE timestamp >= ?` — covered by `idx_ae_timestamp` |
| Filter by `to` timestamp | `WHERE timestamp <= ?` — covered by `idx_ae_timestamp` |
| Combined filters | AND-combined WHERE clauses — composite indexes accelerate common combinations |
| Pagination | `LIMIT ? OFFSET ?` (offset-based) or keyset pagination — **OD-13 PENDING** |
| Deterministic ordering | `ORDER BY timestamp ASC` or `ORDER BY sequence_number ASC` — **OD-14 PENDING** |

---

## 12. Constraints

**Proposed constraints on `audit_event`:**

| Constraint | Column(s) | Type | Justification |
|------------|-----------|------|---------------|
| Primary key | `id` | PK | Uniqueness and lookup |
| NOT NULL | `event_type` | NOT NULL | REQ-A-002 |
| NOT NULL | `actor_id` | NOT NULL | REQ-A-003 |
| NOT NULL | `resource_type` | NOT NULL | REQ-A-004 |
| NOT NULL | `resource_id` | NOT NULL | REQ-A-005 |
| NOT NULL | `timestamp` | NOT NULL | REQ-A-007 |
| NOT NULL | `content_hash` | NOT NULL | REQ-A-010 |
| NOT NULL | `previous_hash` | NOT NULL | REQ-A-011 |
| UNIQUE | `content_hash` | UNIQUE — **PROPOSED, needs review** | Two records should not produce the same hash; collision is theoretically possible but indicates data integrity issue |
| NOT NULL | `sequence_number` | NOT NULL (if adopted) | OD-05 |
| UNIQUE | `sequence_number` | UNIQUE (if adopted) | Monotonic ordering integrity |
| DEFAULT NOW() | `created_at` | DEFAULT | Server-controlled insertion time (if proposed field adopted) |

**Note on `content_hash` UNIQUE constraint:** A UNIQUE constraint prevents two records with identical hashes. This is a safety check — in practice, hash collisions are cryptographically negligible, but identical records (same actor, event, resource, timestamp, payload) would produce identical hashes and may be legitimate. **This constraint must be reviewed before adoption.**

---

## 13. Concurrency and Ordering

**The problem:**

```
  Thread A ──┐
             ├──> Both read record N as the "last record"
  Thread B ──┘
             ┌──> Both set previous_hash = content_hash(N)
             │
  Thread A inserts record N+1  (previous_hash = H(N))  ✓
  Thread B inserts record N+2  (previous_hash = H(N))  ✗ — fork!
```

Two concurrent writes that both read the same predecessor create a fork: two records that both claim to follow record N. The chain can no longer be linearly verified.

**Potential database-level strategies (OD-06 — PENDING):**

| Strategy | Mechanism | Trade-off |
|----------|-----------|-----------|
| `SERIALIZABLE` isolation | PostgreSQL serialisable snapshot isolation detects and aborts conflicting transactions | Simple; adds retry logic complexity |
| `SELECT FOR UPDATE` on last record | Pessimistic lock on the tail record prevents concurrent reads of the same predecessor | Serialises writes; reduces throughput under high concurrency |
| Advisory lock | `pg_advisory_xact_lock(constant)` serialises the append path | Explicit; works across connections |
| Sequence table | A dedicated `chain_sequence` table with a single row, updated atomically | Clear single point of truth for ordering |

**OPEN — final strategy is PENDING ENGINEERING DECISION (OD-06).**

---

## 14. Transaction Boundaries

A single database transaction must cover all of the following steps for each audit event write:

```
BEGIN TRANSACTION
  1. SELECT the most recent record to obtain its content_hash
     (use locking strategy — OD-06)
  2. [Application] Compute content_hash of the new record
  3. INSERT the new record with content_hash and previous_hash
COMMIT
```

**Why atomicity is required:**  
If steps 1 and 3 are in separate transactions, a concurrent write can insert a record between them, silently assigning the wrong `previous_hash` to the new record and breaking the chain.

**If COMMIT fails:** The record is not inserted. The chain remains intact. The caller receives an error. No partial state is written.

The transaction boundary will be enforced at the service layer using Spring's `@Transactional` with an appropriate isolation level (OD-06/OD-07 — PENDING).

---

## 15. Retention Design

Scenario B requires configurable retention of audit records (REQ-B-001–004).

**Retention period:** Configurable via application property — exact default value **OPEN**.

**Mechanism options (OD-08 — PENDING):**

| Option | Description | Effect on Verification |
|--------|-------------|----------------------|
| Hard delete | Remove expired rows from `audit_event` | Chain has a gap at the deletion boundary — verification must handle this |
| Soft delete | Add `archived_at TIMESTAMPTZ` or `is_archived BOOLEAN` column | Row remains in table; verification can distinguish archived records from tampered records |
| Archive table | Move expired rows to `audit_event_archive` | Main table stays clean; verification must query both tables |

**Verification impact (REQ-B-003):** Legitimate archival must not cause the verification endpoint to report a false chain failure. The chosen retention strategy must integrate with the verification walk — the verifier must know how to handle archived/deleted records.

**Proposed column additions for soft-delete option (not finalised):**

| Column | Type | Purpose |
|--------|------|---------|
| `archived_at` | `TIMESTAMPTZ` | Timestamp of archival; NULL if not archived |
| `archive_reason` | `VARCHAR(100)` | `'RETENTION'` or similar |

**OPEN — final retention mechanism is PENDING (OD-08).**

---

## 16. Redaction Design

Scenario B requires structured redaction of sensitive payload fields (REQ-B-005–008).

**The hash integrity problem:**  
`content_hash` was computed over the original, unredacted content. After redaction, the stored `payload` differs from what was hashed. Recomputing `content_hash` from the redacted row will produce a different value — the verification walk will flag this record as tampered.

**This is an inherent trade-off, not a defect.** The architecture and this design must explicitly handle it (REQ-B-008).

**Possible database-level approaches (OD-09 — PENDING):**

| Approach | Mechanism | Implication |
|----------|-----------|-------------|
| Overwrite payload fields | Update the JSONB payload, replacing sensitive values with a redaction marker (e.g., `"[REDACTED]"`) | Simple; original content is gone; hash mismatch on verify |
| Store redaction record | Insert a new audit record of type `REDACTION_EVENT` referencing the original record | Preserves immutable original; redaction is audited |
| Separate redaction flag | Add `is_redacted BOOLEAN` and `redacted_at TIMESTAMPTZ` columns; verifier skips hash check for redacted records | Allows partial verification; relies on application trust |
| Store original hash separately | Add `original_content_hash` column before redaction; verifier uses it for redacted records | Preserves original evidence; more complex verify logic |

**Proposed columns for tracking redaction (not finalised):**

| Column | Type | Purpose |
|--------|------|---------|
| `is_redacted` | `BOOLEAN DEFAULT FALSE` | Flags records that have been redacted |
| `redacted_at` | `TIMESTAMPTZ` | When redaction occurred |
| `redacted_fields` | `TEXT[]` | Which payload fields were redacted |

**OPEN — final redaction mechanism is PENDING (OD-09).**

---

## 17. Export Design

Scenario B requires bulk export of audit records filtered by `resource_id` or `actor_id` (REQ-B-009–013).

**Database considerations:**

- Export queries must include all auditable fields: `event_type`, `actor_id`, `resource_type`, `resource_id`, `payload`, `timestamp`, `content_hash`, `previous_hash`.
- Export must include ordering information (`sequence_number` or `timestamp`) to allow the recipient to reconstruct chain order.
- The genesis record must be identifiable in the export (its `previous_hash` equals the genesis value).
- Export must be deterministically ordered — the same query must produce the same record order on repeated runs.
- The query must retrieve records in chain order (ascending `sequence_number` or `timestamp`) to support sequential offline verification.

**Export format:** PENDING (OD-10) — the database design is format-agnostic; any format (JSON lines, CSV, signed document) can be produced from the same ordered query.

---

## 18. Verification Support

The stored fields in `audit_event` provide everything the application needs to verify the chain:

```
SELECT * FROM audit_event ORDER BY sequence_number ASC  (or timestamp ASC — OD-14)
         │
         ▼
For each record (in order):
  Recompute expected_content_hash = H(canonical(event_type, actor_id, resource_type,
                                                 resource_id, payload, timestamp))
  Check: expected_content_hash == stored content_hash
                                         │
                                   mismatch → tampered record detected
                                   record id + violation type → response

  Check: stored previous_hash == content_hash of previous record
                                         │
                                   mismatch → chain link broken
                                   record id + violation type → response
```

**First record check:** `previous_hash` must equal the defined genesis value (OD-04).

**What direct database tampering looks like:**  
If an attacker modifies `event_type` directly in PostgreSQL, the stored `content_hash` still reflects the original value. Recomputing the hash from the modified field produces a different result — the verifier detects the mismatch.

---

## 19. Security and Sensitive Data

**Application database user (proposed):**

| Permission | Granted |
|------------|---------|
| SELECT on `audit_event` | Yes |
| INSERT on `audit_event` | Yes |
| UPDATE on `audit_event` | **No** |
| DELETE on `audit_event` | **No** |
| CREATE TABLE | No (migrations run separately) |

A dedicated application database user with INSERT + SELECT only enforces append-only behaviour at the database privilege level.

**Credentials:**
- Database credentials must not be committed to source control.
- Credentials must be injected via environment variables or a secrets manager.
- Spring Boot `application.properties` must not contain real passwords.

**Sensitive payload data:**
- `payload` (JSONB) may contain PII.
- The application must not log full payload contents.
- Structured redaction (Scenario B) provides a mechanism for removing sensitive fields.
- Full data-lifecycle governance (GDPR, etc.) is outside assessment scope but noted as a production concern.

---

## 20. Proposed Schema Diagram

```
audit_event
─────────────────────────────────────────────────────────────
id               UUID or BIGSERIAL  PK  NOT NULL
event_type       VARCHAR(255)            NOT NULL
actor_id         VARCHAR(255)            NOT NULL
resource_type    VARCHAR(255)            NOT NULL
resource_id      VARCHAR(255)            NOT NULL
payload          JSONB                   NULL
timestamp        TIMESTAMPTZ             NOT NULL
content_hash     VARCHAR(128)            NOT NULL
previous_hash    VARCHAR(128)            NOT NULL
sequence_number  BIGINT                  NOT NULL (if adopted)
created_at       TIMESTAMPTZ DEFAULT NOW() NOT NULL (if adopted)
is_redacted      BOOLEAN DEFAULT FALSE   (if adopted — Scenario B)
redacted_at      TIMESTAMPTZ             (if adopted — Scenario B)
redacted_fields  TEXT[]                  (if adopted — Scenario B)
archived_at      TIMESTAMPTZ             (if adopted — Scenario B)

─────────────────────────────────────────────────────────────
Indexes:
  PK on id
  idx_ae_actor_id          (actor_id)
  idx_ae_resource_type     (resource_type)
  idx_ae_resource_id       (resource_id)
  idx_ae_event_type        (event_type)
  idx_ae_timestamp         (timestamp)
  idx_ae_sequence_number   (sequence_number)        [if adopted]
  idx_ae_actor_timestamp   (actor_id, timestamp)
  idx_ae_resource_timestamp (resource_type, resource_id, timestamp)
```

---

## 21. Database Decisions

| ID | Topic | Decision | Reason | Status |
|----|-------|----------|--------|--------|
| DB-01 | Database engine | PostgreSQL | Assessment requirement | APPROVED |
| DB-02 | JSONB for payload | Use JSONB for structured payload storage | Queryable; efficient; supports partial redaction | APPROVED |
| DB-03 | `TIMESTAMPTZ` for all timestamps | Use timezone-aware timestamp type; store UTC | Avoids timezone ambiguity | APPROVED |
| DB-04 | Separate `content_hash` and `previous_hash` columns | Two distinct hash columns | Required by hash-chain model (REQ-A-010, REQ-A-011) | APPROVED |
| DB-05 | Primary key type (`UUID` vs `BIGSERIAL`) | PENDING | OD-15 | OPEN |
| DB-06 | Timestamp ownership (server vs. client) | PENDING | OD-01 | OPEN |
| DB-07 | Hash algorithm | PENDING | OD-02 | OPEN |
| DB-08 | Canonical representation for hash | PENDING | OD-03 | OPEN |
| DB-09 | Genesis value | PENDING | OD-04 | OPEN |
| DB-10 | Chain ordering field (`timestamp` vs `sequence_number`) | PENDING | OD-05 | OPEN |
| DB-11 | Concurrency / transaction isolation strategy | PENDING | OD-06 | OPEN |
| DB-12 | Retention mechanism (hard delete / soft delete / archive) | PENDING | OD-08 | OPEN |
| DB-13 | Redaction mechanism and verify behaviour | PENDING | OD-09 | OPEN |
| DB-14 | Pagination strategy (offset vs. keyset) | PENDING | OD-13 | OPEN |
| DB-15 | Query ordering field and direction | PENDING | OD-14 | OPEN |

---

## 22. Database Risks

| ID | Risk | Impact | Mitigation Consideration | Status |
|----|------|--------|--------------------------|--------|
| DR-01 | Concurrent writes fork the hash chain | High | Serialise writes via locking or SERIALIZABLE isolation (OD-06) | OPEN |
| DR-02 | Chain ordering ambiguity if ordering by timestamp with low resolution | High | Use `sequence_number` as authoritative ordering (OD-05) | OPEN |
| DR-03 | Direct database modification by privileged users | Accepted | Detected by `GET /audit/verify`; cannot be prevented at application level | Accepted risk |
| DR-04 | Retention deletes records that are part of an active chain | High | Verification must handle archived records without false failures (OD-08) | OPEN |
| DR-05 | Redaction changes payload after `content_hash` was computed | High | Redaction strategy must define verifier behaviour for redacted records (OD-09) | OPEN |
| DR-06 | Export ordering non-determinism | Medium | Always ORDER BY chain field (OD-14); test determinism | OPEN |
| DR-07 | Large audit history slows verification walk | Low (assessment scope) | No SLA defined; noted for production scale-out | OPEN |
| DR-08 | Sensitive PII in JSONB payload | Medium | Structured redaction (Scenario B); application-level logging restriction | OPEN |
| DR-09 | Excessive indexes slow write throughput | Low (assessment scope) | Index set is minimal; add only on evidence of need | Low risk |
| DR-10 | Transaction failure leaves chain in inconsistent state | High | ACID transaction wraps entire write; no partial commit possible | OPEN — depends on OD-06/07 |

---

## 23. Requirement Traceability

| Requirement | Database Design Element |
|-------------|------------------------|
| REQ-A-002 event_type required | `event_type VARCHAR(255) NOT NULL` |
| REQ-A-003 actor_id required | `actor_id VARCHAR(255) NOT NULL` |
| REQ-A-004 resource_type required | `resource_type VARCHAR(255) NOT NULL` |
| REQ-A-005 resource_id required | `resource_id VARCHAR(255) NOT NULL` |
| REQ-A-006 payload optional | `payload JSONB NULL` |
| REQ-A-007 server timestamp | `timestamp TIMESTAMPTZ NOT NULL` (server-set) |
| REQ-A-010 content hash | `content_hash VARCHAR(128) NOT NULL` |
| REQ-A-011 previous hash | `previous_hash VARCHAR(128) NOT NULL` |
| REQ-A-012 append-only | No UPDATE/DELETE privilege; trigger enforcement (proposed) |
| REQ-A-014 filter actorId | `idx_ae_actor_id` index |
| REQ-A-015 filter resourceType | `idx_ae_resource_type` index |
| REQ-A-016 filter resourceId | `idx_ae_resource_id` index |
| REQ-A-017 filter eventType | `idx_ae_event_type` index |
| REQ-A-018–019 time range | `idx_ae_timestamp` index |
| REQ-A-020 combined filters | Composite indexes |
| REQ-A-022 pagination | `LIMIT` / `OFFSET` or keyset (OD-13) |
| REQ-A-023 deterministic ordering | ORDER BY `sequence_number` or `timestamp` (OD-14) |
| REQ-A-024–030 hash chain | `content_hash`, `previous_hash`, transaction boundary |
| REQ-A-031–036 verification | All stored fields; chain order; `content_hash`/`previous_hash` |
| REQ-B-001–004 retention | `archived_at` column (if soft-delete adopted — OD-08) |
| REQ-B-005–008 redaction | `is_redacted`, `redacted_at`, `redacted_fields` columns (OD-09) |
| REQ-B-009–013 export | Ordered SELECT of all auditable + chain fields |

---

## 24. Database Design Review Checklist

- [ ] Audit event fields reviewed
- [ ] Primary key strategy reviewed
- [ ] Timestamp strategy reviewed
- [ ] Hash-chain fields reviewed
- [ ] Canonicalisation impact reviewed
- [ ] Indexes reviewed
- [ ] Constraints reviewed
- [ ] Concurrency considered
- [ ] Transaction boundary considered
- [ ] Retention considered
- [ ] Redaction considered
- [ ] Export considered
- [ ] Verification considered
- [ ] Security considerations reviewed
- [ ] Open decisions identified
