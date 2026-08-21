# Requirement Analysis

**Project:** Audit Log Service  
**Engineer:** Vrushali Mahadik  
**Status:** Requirements analysed and implemented; historical proposal and decision records are preserved below.

> **Final implementation note:** The requirement statements and original ambiguity records remain unchanged in meaning. The implemented decisions and validation status are summarized here and detailed in the architecture, API, database, Scenario C, and testing documents.

### Final Implementation Status

- Scenario A core audit creation, persistence, querying, pagination, deterministic ordering, hash chaining, verification, and tamper detection are implemented.
- Scenario B retention, structured redaction, bulk export, and admin authorization are implemented.
- Scenario C is implemented using the conservative decisions recorded in `docs/SCENARIO-C-DESIGN.md`.
- OAuth2/JWT resource-server authentication and role-based authorization are implemented.
- PostgreSQL schema management uses Flyway V1 and V2 migrations with Spring JDBC persistence.
- Testing evidence records 39 executable tests passed, 0 failures, 0 errors, and 38 PostgreSQL/Testcontainers tests not executed because Docker was unavailable.

---

## 1. Assessment Overview

### Assessment Objective

Design and implement a tamper-evident audit log service as a demonstration of AI-assisted software engineering. The service must record immutable, integrity-verifiable audit events and expose a queryable REST API.

### Expected Engineering Outcome

A working Java 21 / Spring Boot / PostgreSQL service that:

- Accepts and persists structured audit events.
- Enforces an append-only, hash-chained audit trail.
- Exposes filtered query and verification endpoints.
- Addresses additional scenarios covering retention, structured redaction, and bulk export.
- Handles a partially specified scenario (Scenario C) through a documented clarification and assumption process.

### AI-Assisted Engineering Expectations

The assessment explicitly requires that AI tooling (GitHub Copilot or equivalent) be used as an engineering assistant. Every meaningful AI interaction must be recorded in `ai/ai-usage.md`. The engineer makes all final decisions; AI does not make engineering decisions autonomously.

### Human Engineering Responsibility

Vrushali Mahadik is responsible for:

- Reviewing all AI-generated output before acceptance.
- Making all Git commits and pushes.
- Validating correctness, security, and design decisions.
- Recording AI interactions in `ai/ai-usage.md`.

### Required Development Evidence

- Populated `ai/ai-usage.md` with real interaction history.
- `docs/REQUIREMENT-ANALYSIS.md` (this document).
- `docs/TESTING-EVIDENCE.md` with actual test results.
- Working application with passing tests.
- Git history reflecting incremental, reviewable development.

---

## 2. Core Problem

### Tamper-Evident Audit Log

An audit log is only useful as evidence if it cannot be silently altered after the fact. The assessment requires a hash-chained audit log that makes historical modification detectable.

**Append-Only Audit History**  
Records are written once and never updated or deleted through normal application paths. No UPDATE or DELETE of persisted audit records is permitted via the API.

**Audit Event Integrity**  
Each record must include a cryptographic hash of its own content, computed deterministically from a canonical representation of its auditable fields.

**Event Content Hash**  
A hash computed over the auditable fields of a single event (e.g., eventType, actorId, resourceType, resourceId, payload, timestamp). The exact canonical form and algorithm are PENDING engineering decision (see Section 15).

**Previous-Record Hash**  
Each record includes the content hash of the immediately preceding record in the chain, establishing a linked chain across the full audit history.

**Genesis Value**  
The first record in the chain has no predecessor. A defined genesis value must be used as the `previousHash` for the first record. The exact genesis value is PENDING engineering decision (see Section 15).

**Detection of Historical Modification**  
A verification endpoint must be able to walk the entire chain, recompute each content hash from stored fields, and confirm that each `previousHash` matches the prior record's stored `contentHash`. Any mismatch indicates tampering.

---

## 3. Functional Requirements

### Naming Convention

| Prefix | Domain |
|--------|--------|
| REQ-A  | Scenario A — Core Audit Log Service |
| REQ-B  | Scenario B — Retention, Redaction, Export |
| REQ-C  | Scenario C — Clarification Pending |
| SEC    | Security Requirements |
| DATA   | Data Requirements |
| TEST   | Quality and Testing Requirements |
| NFR    | Non-Functional Requirements |

---

## 4. Scenario A — Core Audit Log Service

### 4.1 Write

| ID | Requirement |
|----|-------------|
| REQ-A-001 | The service must expose a `POST /audit` endpoint to record a new audit event. |
| REQ-A-002 | The request body must include `eventType`. |
| REQ-A-003 | The request body must include `actorId`. |
| REQ-A-004 | The request body must include `resourceType`. |
| REQ-A-005 | The request body must include `resourceId`. |
| REQ-A-006 | The request body may include `payload` (arbitrary structured data). |
| REQ-A-007 | The service must assign a server-side `timestamp`. Whether the client may supply a timestamp, or the server always overrides it, is PENDING engineering decision. |
| REQ-A-008 | All required fields must be validated. Missing or blank required fields must return a 400 response. |
| REQ-A-009 | Valid events must be persisted to the database. |
| REQ-A-010 | A `contentHash` must be computed from the event's auditable fields and stored with the record. |
| REQ-A-011 | A `previousHash` must be stored on each record, linking it to its predecessor in the chain. |
| REQ-A-012 | The audit log must be append-only. No update or delete of audit records is permitted through the API. |

### 4.2 Query

| ID | Requirement |
|----|-------------|
| REQ-A-013 | The service must expose a `GET /audit` endpoint to query recorded events. |
| REQ-A-014 | The query must support filtering by `actorId`. |
| REQ-A-015 | The query must support filtering by `resourceType`. |
| REQ-A-016 | The query must support filtering by `resourceId`. |
| REQ-A-017 | The query must support filtering by `eventType`. |
| REQ-A-018 | The query must support filtering by a `from` timestamp (inclusive). |
| REQ-A-019 | The query must support filtering by a `to` timestamp (inclusive). |
| REQ-A-020 | Multiple filters must be combinable in a single request. |
| REQ-A-021 | Invalid query parameters must return a descriptive 400 response. |
| REQ-A-022 | The query must support pagination. |
| REQ-A-023 | Results must be returned in deterministic order (ordering field and direction are PENDING engineering decision). |

### 4.3 Hash Chain

| ID | Requirement |
|----|-------------|
| REQ-A-024 | Each audit record must store a `contentHash` computed from its auditable fields. |
| REQ-A-025 | Each audit record must store a `previousHash` equal to the `contentHash` of the immediately preceding record. |
| REQ-A-026 | The first record in the chain must use a defined genesis value as its `previousHash`. |
| REQ-A-027 | The canonical representation used to compute `contentHash` must be deterministic and documented. — **PENDING** |
| REQ-A-028 | Hash ordering (which record is "previous") must be defined and consistently applied. — **PENDING** |
| REQ-A-029 | Concurrent writes must not corrupt the chain. A concurrency and transaction boundary strategy is required. — **PENDING** |
| REQ-A-030 | Hash computation and record persistence must occur within a single transaction to prevent partial state. — **PENDING** |

### 4.4 Verification

| ID | Requirement |
|----|-------------|
| REQ-A-031 | The service must expose a `GET /audit/verify` endpoint. |
| REQ-A-032 | The verification endpoint must walk the entire chain and recompute each `contentHash` from stored fields. |
| REQ-A-033 | The response must indicate whether the chain is intact (`valid: true/false`). |
| REQ-A-034 | If the chain is broken, the response must identify the first inconsistent record. |
| REQ-A-035 | The response must describe the violation type (e.g., hash mismatch, missing predecessor). |
| REQ-A-036 | The endpoint must detect records modified directly in the database (bypassing the API). |

---

## 5. Scenario B

### 5.1 Retention

| ID | Requirement |
|----|-------------|
| REQ-B-001 | The service must support configurable retention of audit records. |
| REQ-B-002 | Records outside the retention window must be archived or soft-deleted. The mechanism is PENDING engineering decision. |
| REQ-B-003 | Legitimate archival of expired records must not cause the verification endpoint to report a false chain failure. |
| REQ-B-004 | The retention period must be externally configurable (e.g., application property). |

### 5.2 Structured Redaction

| ID | Requirement |
|----|-------------|
| REQ-B-005 | The service must support structured redaction of sensitive fields within the `payload` of an audit record. |
| REQ-B-006 | Redaction must not destroy the record's tamper-evidence properties. The approach and its trade-offs must be documented. |
| REQ-B-007 | After redaction, the verification endpoint behaviour with respect to the redacted record must be clearly defined and tested. |
| REQ-B-008 | The limitations of redaction with respect to hash-chain integrity must be documented. The original `contentHash` was computed over the unredacted content; redaction inherently changes the verifiable content. — **TRADE-OFF: see Section 16** |

### 5.3 Bulk Export

| ID | Requirement |
|----|-------------|
| REQ-B-009 | The service must support bulk export of audit records filtered by `resourceId` or `actorId`. |
| REQ-B-010 | The export must be self-contained and independently verifiable without access to the running service. |
| REQ-B-011 | The export must include sufficient chain metadata to allow offline verification. |
| REQ-B-012 | The export format is PENDING engineering decision. |
| REQ-B-013 | Verification information (hashes, chain anchors) must be included in the export. |

---

## 6. Scenario C

> **Final status:** The original clarification questions below are historical source material. Scenario C was resolved and implemented using the final decisions in `docs/SCENARIO-C-DESIGN.md`; it is no longer pending for the current implementation.

### Statement of Ambiguity

Scenario C is not fully specified in the assessment. The scenario involves audit logging for a specific access or activity context, but the following questions are unresolved and require clarification before design can proceed.

**All items below are marked PENDING CLARIFICATION.**

| # | Clarification Question |
|---|----------------------|
| C-Q-01 | What does "access" mean in this context — read access, write access, or both? |
| C-Q-02 | Should successful access be recorded, failed access attempts, or both? |
| C-Q-03 | What is the scope of "client account data"? |
| C-Q-04 | Who are the actors in this scenario? (end users, service accounts, administrators?) |
| C-Q-05 | What event types are required? |
| C-Q-06 | Is there a regulator or external auditor who requires visibility into this log? |
| C-Q-07 | Does Scenario C have different retention requirements from Scenario A? |
| C-Q-08 | Does Scenario C have different redaction requirements? |
| C-Q-09 | Who has reporting access and through which interface? |
| C-Q-10 | Is time-range filtering required for Scenario C reporting? |
| C-Q-11 | Are there additional filters specific to Scenario C? |
| C-Q-12 | Must access be recorded via direct API calls, or also via exported reports? |

### Expected Process

```
Ambiguous requirement
  → Clarification questions
    → Documented assumptions (if clarification unavailable)
      → Normalised requirement statement
        → Design decision
          → Scoped implementation
            → Test evidence
```

The original assessment wording left Scenario C pending clarification. That historical state was resolved through documented assumptions and a normalized requirement; the current implementation records successful READ/WRITE client-account access through the dedicated Scenario C endpoint.

---

## 7. Authentication and Authorization

> **Final implementation:** JWT resource-server authentication is configured through `SecurityConfig`. `SERVICE` and `ADMIN` may write, `AUDITOR` and `ADMIN` may query, and `ADMIN` is required for verification, retention, redaction, and export. The original pending labels in the requirement tables are retained as historical decision-state records.

### Assessment Requirement vs. Production Enhancement

The assessment does not explicitly mandate a specific authentication mechanism (e.g., JWT, OAuth2, API keys). What the assessment does require is that:

- Administrative operations (e.g., verification, bulk export, redaction) are protected.
- Unauthorized actors cannot write or read audit records inappropriately.
- The authentication and authorization approach must be documented and justified.

The specific mechanism is an **engineering decision** — PENDING.

### Identified Requirements

| ID | Requirement | Source |
|----|-------------|--------|
| SEC-001 | Write access to `POST /audit` must be restricted to authorised service accounts or users. | Engineering requirement |
| SEC-002 | Read access to `GET /audit` must be restricted by role. | Engineering requirement |
| SEC-003 | `GET /audit/verify` is an administrative operation and must require elevated privilege. | Engineering requirement |
| SEC-004 | Bulk export must require elevated privilege. | Engineering requirement |
| SEC-005 | Redaction must require elevated privilege. | Engineering requirement |
| SEC-006 | The chosen authentication mechanism must be documented with its rationale. | Assessment expectation |

---

## 8. Security Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| SEC-007 | All API endpoints must require authentication. Mechanism PENDING. | PENDING |
| SEC-008 | Role-based authorization must restrict access to sensitive operations. | PENDING |
| SEC-009 | Credentials, secrets, and API keys must not be committed to source control. | Engineering requirement |
| SEC-010 | If JWT is selected: token signature must be validated; expired tokens must be rejected. | PENDING |
| SEC-011 | All incoming data must be validated; invalid input must produce a 400 with a safe message. | REQ-A-008 / REQ-A-021 |
| SEC-012 | Unauthorized requests must receive a 401 or 403 response; no sensitive detail in the error body. | PENDING |
| SEC-013 | Administrative operations (verify, redact, export) must be protected. | SEC-003–005 |
| SEC-014 | Safe error responses must not expose stack traces, internal paths, or database schema details. | Engineering requirement |
| SEC-015 | Secret management strategy must be defined (e.g., environment variables, secrets manager). | PENDING |

---

## 9. Data Requirements

### Required Audit Event Fields

| Field | Source | Notes |
|-------|--------|-------|
| `id` | Engineering | Unique identifier; generation strategy PENDING (UUID vs. sequential). |
| `eventType` | Assessment | Required on write. |
| `actorId` | Assessment | Required on write. |
| `resourceType` | Assessment | Required on write. |
| `resourceId` | Assessment | Required on write. |
| `payload` | Assessment | Optional structured data. |
| `timestamp` | Assessment | Server-assigned. Client-supplied vs. server-override is PENDING. |
| `contentHash` | Assessment | Computed from auditable fields. Algorithm and canonical form PENDING. |
| `previousHash` | Assessment | Hash of the preceding record; genesis value for first record PENDING. |

### Proposed Additional Fields

The following fields are proposed engineering decisions — not explicitly required by the assessment. They must be reviewed and approved before implementation.

| Field | Rationale | Status |
|-------|-----------|--------|
| `createdAt` | Server-controlled insertion time, distinct from logical `timestamp`. | PROPOSED |
| `sequenceNumber` | Monotonic ordering for unambiguous chain traversal. | PROPOSED |
| `version` | Schema version for forward compatibility. | PROPOSED |

---

## 10. API Requirements

### POST /audit — Record Audit Event

| Attribute | Detail |
|-----------|--------|
| Method | POST |
| Endpoint | `/audit` |
| Purpose | Persist a new tamper-evident audit event. |
| Request body | JSON: `eventType`, `actorId`, `resourceType`, `resourceId`, `payload` (optional) |
| Response (201) | The persisted record including generated `id`, `timestamp`, `contentHash`, `previousHash`. |
| Validation | All required fields present and non-blank. |
| Authorization | Requires authenticated, authorized caller. — PENDING |
| Error — 400 | Missing or invalid required fields. |
| Error — 401/403 | Unauthenticated or unauthorised. — PENDING |

### GET /audit — Query Audit Events

| Attribute | Detail |
|-----------|--------|
| Method | GET |
| Endpoint | `/audit` |
| Purpose | Query audit events with optional filters. |
| Query parameters | `actorId`, `resourceType`, `resourceId`, `eventType`, `from`, `to`, `page`, `size` |
| Response (200) | Paginated list of matching audit events in deterministic order. |
| Validation | Invalid parameter formats return 400. |
| Authorization | Requires authenticated, authorized caller. — PENDING |
| Error — 400 | Invalid query parameter format. |
| Error — 401/403 | Unauthenticated or unauthorised. — PENDING |
| Pagination | Required. Strategy PENDING. |
| Ordering | Deterministic. Field and direction PENDING. |

### GET /audit/verify — Verify Chain Integrity

| Attribute | Detail |
|-----------|--------|
| Method | GET |
| Endpoint | `/audit/verify` |
| Purpose | Walk the full hash chain and report integrity. |
| Request | No body. Optional query parameters TBD. |
| Response (200) | `{ "valid": true/false, "firstInconsistentRecord": ..., "violationType": ... }` |
| Authorization | Requires elevated privilege. — PENDING |
| Error — 401/403 | Unauthenticated or unauthorised. — PENDING |
| Error cases | Chain broken: 200 with `valid: false` and diagnostic fields populated. |

### Bulk Export Endpoint (Scenario B)

| Attribute | Detail |
|-----------|--------|
| Method | GET (or POST — PENDING) |
| Endpoint | TBD |
| Purpose | Export a self-contained, independently verifiable set of audit records. |
| Filters | `resourceId` or `actorId` — PENDING specification |
| Response | Self-contained export with chain metadata. Format PENDING. |
| Authorization | Requires elevated privilege. — PENDING |

---

## 11. Non-Functional Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| NFR-001 | Security: all endpoints authenticated and authorised. | PENDING |
| NFR-002 | Reliability: append-only writes must be atomic; partial writes are not acceptable. | Engineering requirement |
| NFR-003 | Maintainability: code must be readable, tested, and documented at the level expected for a professional deliverable. | Assessment expectation |
| NFR-004 | Testability: business logic must be unit-testable independent of the database. | Engineering requirement |
| NFR-005 | Performance: verification of a large chain must complete in a reasonable time. No explicit SLA defined in the assessment. | OPEN |
| NFR-006 | Deterministic behaviour: hash computation must produce the same output for the same input on every run. | Assessment requirement |
| NFR-007 | Observability: errors must be logged with sufficient context for diagnosis; no secrets in logs. | Engineering requirement |
| NFR-008 | Documentation: API behaviour, design decisions, and trade-offs must be documented. | Assessment expectation |
| NFR-009 | Reproducibility: the project must build and run from a clean checkout. | Assessment expectation |

---

## 12. Quality Requirements

| ID | Requirement | Status |
|----|-------------|--------|
| TEST-001 | The project must build cleanly: `mvn clean package`. | NOT RUN |
| TEST-002 | Unit tests must pass covering business logic (hash computation, validation, chain logic). | NOT RUN |
| TEST-003 | Integration tests must verify end-to-end API behaviour against a real or in-memory database. | NOT RUN |
| TEST-004 | A tamper-evidence test must directly modify a record in the database and confirm the verification endpoint detects the breach. | NOT RUN |
| TEST-005 | Static analysis / linting should produce no critical findings. Tool selection PENDING. | NOT RUN |
| TEST-006 | No secrets or credentials must appear in source code or test fixtures. | NOT RUN |
| TEST-007 | CI or local quality gates must be defined and documented. | PENDING |

---

## 13. AI-Assisted Engineering Requirements

The assessment requires that AI tooling be used as an engineering assistant and that usage be audited.

Every meaningful AI/Copilot interaction must be recorded in `ai/ai-usage.md` with:

| Field | Description |
|-------|-------------|
| AI Interaction ID | Sequential identifier (AI-001, AI-002, …) |
| Date | Date of interaction |
| Development step | Which assessment step it relates to |
| Prompt intention | Why AI was consulted |
| Exact prompt | The literal prompt used (or `[not recorded]` for historical entries) |
| AI output summary | What the AI produced |
| Engineer decision | ACCEPT / MODIFY / REJECT |
| Engineer review | The engineer's assessment of the output |
| Modifications made | What the engineer changed |
| Reason for decision | Justification |
| Validation performed | How the output was verified |
| Related Git commit | Commit reference |

AI does not commit, push, or make final engineering decisions. Vrushali Mahadik retains all engineering authority.

---

## 14. Requirement Traceability

| ID | Requirement | Source / Scenario | Design | Implementation | Test | Evidence | Status |
|----|-------------|-------------------|--------|----------------|------|----------|--------|
| REQ-A-001 | POST /audit endpoint | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-002 | eventType required | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-003 | actorId required | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-004 | resourceType required | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-005 | resourceId required | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-006 | payload optional | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-007 | server-side timestamp | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-008 | input validation — 400 | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-009 | persist valid events | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-010 | contentHash computed | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-011 | previousHash stored | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-012 | append-only | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-013 | GET /audit endpoint | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-014 | filter by actorId | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-015 | filter by resourceType | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-016 | filter by resourceId | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-017 | filter by eventType | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-018 | filter from timestamp | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-019 | filter to timestamp | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-020 | combined filters | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-021 | query validation — 400 | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-022 | pagination | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-023 | deterministic ordering | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-024 | contentHash per record | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-025 | previousHash per record | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-026 | genesis value | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-027 | canonical hash form | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-028 | hash ordering definition | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-029 | concurrency strategy | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-030 | atomic transaction | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-031 | GET /audit/verify | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-032 | chain walk and recompute | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-033 | valid/invalid result | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-034 | first inconsistent record | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-035 | violation type | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-A-036 | direct DB tamper detection | Scenario A | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-001 | configurable retention | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-002 | archive / soft-delete | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-003 | archival no false failure | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-004 | retention configurable | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-005 | structured redaction | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-006 | redaction + tamper evidence | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-007 | verify after redaction | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-008 | redaction trade-off documented | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-009 | bulk export by resourceId/actorId | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-010 | self-contained export | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-011 | chain metadata in export | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-012 | export format | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-B-013 | verification info in export | Scenario B | PENDING | PENDING | PENDING | PENDING | Open |
| REQ-C-* | Scenario C requirements | Scenario C | PENDING CLARIFICATION | — | — | — | Blocked |
| SEC-001–015 | Security requirements | Engineering | PENDING | PENDING | PENDING | PENDING | Open |
| DATA-* | Audit event data model | Assessment + Engineering | PENDING | PENDING | PENDING | PENDING | Open |
| NFR-001–009 | Non-functional requirements | Engineering | PENDING | PENDING | PENDING | PENDING | Open |
| TEST-001–007 | Quality requirements | Assessment | PENDING | PENDING | PENDING | PENDING | Open |

---

## 15. Open Decisions

The following engineering decisions must be made before or during implementation. None are resolved at this stage.

| # | Decision | Options Under Consideration | Status |
|---|----------|-----------------------------|--------|
| OD-01 | Timestamp ownership: client-supplied or server-assigned? | Server-always / Client-with-server-override | PENDING |
| OD-02 | Hash algorithm | SHA-256, SHA-3-256 | PENDING |
| OD-03 | Canonical representation for contentHash | JSON (sorted keys), field-concatenation | PENDING |
| OD-04 | Genesis value for first record's previousHash | Fixed string "GENESIS", zero-hash (64 zeros), null | PENDING |
| OD-05 | Chain ordering: what defines "previous"? | Insertion order / monotonic sequence number / timestamp | PENDING |
| OD-06 | Concurrency: how to prevent two concurrent writes racing on previousHash? | Serialisable transaction / pessimistic lock / sequence table | PENDING |
| OD-07 | Transaction boundary for write + hash computation | Single transaction, hash in application layer | PENDING |
| OD-08 | Retention mechanism | Hard delete with archived log / soft-delete flag / partition | PENDING |
| OD-09 | Redaction mechanism | Null-out fields / replace with redaction marker / hash of redacted value | PENDING |
| OD-10 | Export format | JSON lines / CSV / signed JSON document | PENDING |
| OD-11 | Authentication implementation | JWT (self-signed) / OAuth2 / API key / Spring Security Basic | PENDING |
| OD-12 | Authorization matrix | Role definitions and permissions per endpoint | PENDING |
| OD-13 | Pagination strategy | Offset-based / cursor-based | PENDING |
| OD-14 | Ordering field and direction for GET /audit | timestamp ASC / sequence ASC | PENDING |
| OD-15 | Record ID type | UUID / BIGSERIAL | PENDING |

---

## 16. Risks and Trade-offs

| # | Risk / Trade-off | Impact | Mitigation |
|---|-----------------|--------|------------|
| R-01 | Concurrent writes could cause two records to claim the same predecessor, corrupting the chain. | High | Serialise writes using a DB lock or sequence. Strategy PENDING. |
| R-02 | Chain ordering ambiguity: if timestamp has millisecond collisions, ordering becomes non-deterministic. | Medium | Use a monotonic sequence number as the definitive ordering field. PENDING. |
| R-03 | Direct database modification cannot be prevented at the application layer. | Accepted | Detected by the verify endpoint. Must be covered by a tamper-evidence test. |
| R-04 | Retention (archive/delete) of old records invalidates the chain for records that referenced the deleted records. | High | Verification must account for archived records; the archival strategy must be designed to avoid false failures (REQ-B-003). |
| R-05 | Redaction changes the content of a record after the original contentHash was computed. The original hash can no longer be verified against the redacted content. | High | Must be documented as a known limitation. Redaction strategy must decide whether to recompute the hash or preserve the original (REQ-B-008). |
| R-06 | Exported records cannot call back to the live service for verification — export must be self-contained. | Medium | Export must include all chain metadata and a verification manifest. |
| R-07 | Privileged users (DBAs) can modify records directly. | Accepted | Tamper detection via verify endpoint. Operational controls outside application scope. |
| R-08 | Large chain verification may be slow for a mature service. | Low (for assessment) | No SLA defined; noted as a scalability concern. |
| R-09 | Sensitive data in payload: if payload contains PII, it must be handled under GDPR or equivalent. | Medium | Structured redaction (Scenario B) partially addresses this; full data-lifecycle policy is outside assessment scope. |

---

## 17. Definition of Done

The following checklist defines completion of the full assessment. Items remain unchecked until verified.

### Project Foundation
- [ ] Java 21 / Spring Boot / Maven project builds cleanly.
- [ ] Application starts without errors.
- [ ] Project structure follows standard Spring Boot conventions.

### Scenario A — Core
- [ ] `POST /audit` accepts and validates audit events.
- [ ] `GET /audit` queries with all supported filters.
- [ ] Pagination is implemented and tested.
- [ ] Deterministic ordering is implemented and tested.
- [ ] `contentHash` is computed and stored on every record.
- [ ] `previousHash` links every record to its predecessor.
- [ ] Genesis value is applied to the first record.
- [ ] Hash chain is verifiable end-to-end.
- [ ] `GET /audit/verify` correctly identifies a clean chain.
- [ ] `GET /audit/verify` correctly detects direct database tampering.
- [ ] First inconsistent record is identified on failure.
- [ ] Violation type is included in the verify response.

### Scenario B
- [ ] Configurable retention is implemented.
- [ ] Archival/soft-delete does not cause false verify failures.
- [ ] Structured redaction is implemented with documented trade-offs.
- [ ] Bulk export produces a self-contained, independently verifiable artifact.

### Scenario C
- [ ] Clarification questions answered or assumptions documented.
- [ ] Normalised requirements written.
- [ ] Implementation complete and tested.

### Security
- [ ] All endpoints require authentication.
- [ ] Role-based authorization enforced.
- [ ] No credentials in source control.
- [ ] Safe error responses — no stack traces or internal details exposed.
- [ ] Secrets management strategy documented and applied.

### Quality
- [ ] All unit tests pass.
- [ ] All integration tests pass.
- [ ] Tamper-evidence test passes.
- [ ] No critical static analysis findings.
- [ ] Build is reproducible from a clean checkout.

### Evidence
- [ ] `ai/ai-usage.md` fully populated with real interaction history.
- [ ] `docs/TESTING-EVIDENCE.md` populated with actual test results.
- [ ] All engineering decisions documented.
- [ ] Git history reflects incremental, reviewable development.
