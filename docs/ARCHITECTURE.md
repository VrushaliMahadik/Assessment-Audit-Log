# Architecture — Audit Log Service

**Engineer:** Vrushali Mahadik  
**Status:** Proposed — subject to engineering review. Unresolved decisions remain open until approved.

---

## 1. Purpose

This document translates the approved requirements from `docs/REQUIREMENT-ANALYSIS.md` into a proposed technical architecture for the Audit Log Service before any implementation begins.

It serves as the design contract between the requirements phase and the implementation phase. All major components, flows, and boundaries are defined here so that implementation can proceed with a clear, reviewable plan.

**Important:** Several engineering decisions documented in `docs/REQUIREMENT-ANALYSIS.md` remain unresolved. Those decisions are preserved as open in this document. No open decision has been silently resolved. Final architecture is subject to engineering review and approval.

---

## 2. Architecture Goals

The following goals drive all architectural decisions. Each is supported by the assessment or the approved requirements.

| Goal | Source |
|------|--------|
| Tamper-evident audit history | Assessment — core requirement |
| Append-only audit records | REQ-A-012 |
| Reliable, durable persistence | NFR-002 |
| Deterministic hash-chain behaviour | REQ-A-024–030 |
| Queryability with filters and pagination | REQ-A-013–023 |
| Chain verification | REQ-A-031–036 |
| Configurable retention support | REQ-B-001–004 |
| Structured redaction with documented trade-offs | REQ-B-005–008 |
| Independently verifiable bulk export | REQ-B-009–013 |
| Authentication and role-based authorisation | SEC-001–015 |
| Testability of business logic independent of the database | NFR-004 |
| Maintainable, readable codebase | NFR-003 |

---

## 3. Architecture Overview

The Audit Log Service is a single Spring Boot application. There are no microservices — a modular monolith is appropriate for the assessment scope.

```
Client (HTTP)
     │
     ▼
REST API Layer
(Controllers — request/response mapping, input validation, HTTP semantics)
     │
     ▼
Security Layer
(Authentication filter, role-based authorisation)
     │
     ▼
Service / Domain Layer
(Audit logic, hash computation, chain management, verification, retention, redaction, export)
     │
     ▼
Persistence Layer
(Spring Data repositories, query building, transactional behaviour)
     │
     ▼
PostgreSQL
(Durable storage of audit events)
```

**Layer responsibilities:**

- **REST API Layer** — translates HTTP requests into domain operations. Performs structural input validation. Returns well-formed HTTP responses. Does not contain business logic.
- **Security Layer** — intercepts all requests, enforces authentication and authorisation before the controller is reached. Details PENDING (see Section 15).
- **Service / Domain Layer** — owns all business rules: hash computation, chain linking, append-only enforcement, verification, and orchestration of Scenario B operations.
- **Persistence Layer** — abstracts database access. Responsible for transactional writes, filter-based queries, and pagination.
- **PostgreSQL** — provides durable, ACID-compliant storage.

---

## 4. Component Architecture

### 4.1 API Layer

Responsibilities:
- Expose HTTP endpoints: `POST /audit`, `GET /audit`, `GET /audit/verify`, and Scenario B endpoints (TBD).
- Validate that required fields are present and well-formed before passing to the service layer.
- Map service results to HTTP response bodies.
- Return safe, descriptive error responses (no stack traces, no internal detail).
- Handle content negotiation and pagination response wrappers.

### 4.2 Security Layer

Responsibilities:
- Authenticate every request to a protected endpoint before it reaches a controller.
- Make the authenticated identity (user, role) available to downstream layers for authorisation decisions.
- Return 401 for missing or invalid credentials.
- Return 403 for authenticated but unauthorised requests.
- Ensure credentials and tokens are never logged.

Authentication mechanism: **PENDING ENGINEERING DECISION** (see Section 15).  
Authorisation matrix: **PENDING ENGINEERING REVIEW** (see Section 16).

### 4.3 Audit Service / Domain Layer

Responsibilities:
- Validate audit event content beyond structural checks (business rules).
- Resolve the previous record in the chain to obtain its `contentHash`.
- Produce a canonical representation of the new event's auditable fields.
- Compute the `contentHash` over that canonical representation.
- Persist the new record with its `contentHash` and `previousHash` inside a single transaction.
- Enforce append-only behaviour — expose no update or delete operations.
- Walk the chain during verification: recompute each record's `contentHash` from stored fields and validate each `previousHash` reference.
- Orchestrate Scenario B operations: retention, redaction, and export.

### 4.4 Persistence Layer

Responsibilities:
- Store audit events to PostgreSQL.
- Retrieve a single record, a filtered page of records, and the full ordered chain.
- Execute all writes in a transaction that covers both hash computation and record insertion.
- Support the query filters defined in REQ-A-014–021.
- Return results in deterministic order.

### 4.5 PostgreSQL

Provides durable, ACID-compliant storage for audit events. Schema design is PENDING (dependent on open decisions: record ID type OD-15, sequence field OD-05, timestamp ownership OD-01).

---

## 5. Proposed Package Structure

```
src/main/java/com/vrushali/auditlog/

    config/          ← Spring configuration, security beans, application properties
    controller/      ← REST controllers (API Layer)
    dto/             ← Request and response objects (no JPA annotations)
    service/         ← Business logic, hash computation, verification, Scenario B
    repository/      ← Spring Data JPA interfaces
    entity/          ← JPA entities (audit event record)
    exception/       ← Custom exceptions and global exception handler
    validation/      ← Custom validators for request inputs
    security/        ← Authentication/authorisation configuration and filters
```

> **Proposed structure — implementation will follow after design approval.**

`dto/` and `entity/` are intentionally separated so that the persistence model is not leaked to the API layer and the domain layer can evolve independently.

---

## 6. Audit Event Write Flow

```
Client
  │
  ▼  POST /audit  {eventType, actorId, resourceType, resourceId, payload}
Controller
  │  Structural validation (required fields present, correct types)
  │  Return 400 if invalid
  ▼
Security Layer
  │  Authenticate caller
  │  Authorise write operation
  │  Return 401/403 if rejected
  ▼
Audit Service
  │
  ├─ Validate business rules (e.g., eventType not blank)
  │
  ├─ BEGIN TRANSACTION
  │
  ├─ Determine previous record (most recent persisted record)
  │   Obtain its contentHash as the new record's previousHash
  │   Use genesis value if no previous record exists
  │
  ├─ Assign server-side timestamp  [OD-01 — server-assigned]
  │
  ├─ Build canonical representation of auditable fields  [OD-03 PENDING]
  │
  ├─ Compute contentHash over canonical representation  [OD-02 PENDING]
  │
  ├─ Persist new record (id, eventType, actorId, resourceType, resourceId,
  │   payload, timestamp, contentHash, previousHash)
  │
  └─ COMMIT TRANSACTION
  ▼
Controller
  │  Map persisted record to response DTO
  ▼
Client  ← 201 Created  {id, timestamp, contentHash, previousHash, ...}
```

**Each stage explained:**

- **Structural validation** — controller responsibility; fast-fail before any business logic.
- **Authentication/authorisation** — security layer; caller must be known and permitted.
- **Previous-record resolution** — determines the hash of the record immediately preceding the new one. Must be done inside the transaction to prevent race conditions (concurrency strategy PENDING — OD-06).
- **Canonical representation** — produces the deterministic string or byte sequence that will be hashed (OD-03 PENDING).
- **Hash computation** — applies the chosen algorithm to the canonical representation (OD-02 PENDING).
- **Transactional persist** — the entire sequence from previous-record lookup to INSERT is a single transaction (OD-07).

---

## 7. Hash Chain Architecture

```
  ┌──────────────────────────────────┐
  │  Record 1 (genesis)              │
  │  contentHash  = H(fields_1)      │
  │  previousHash = GENESIS_VALUE    │
  └──────────────────────────────────┘
              │
              │ previousHash
              ▼
  ┌──────────────────────────────────┐
  │  Record 2                        │
  │  contentHash  = H(fields_2)      │
  │  previousHash = contentHash(R1)  │
  └──────────────────────────────────┘
              │
              │ previousHash
              ▼
  ┌──────────────────────────────────┐
  │  Record N                        │
  │  contentHash  = H(fields_N)      │
  │  previousHash = contentHash(R(N-1)) │
  └──────────────────────────────────┘
```

**Principles:**

- Every record carries its own `contentHash`, computed deterministically from its auditable fields.
- Every record carries the `contentHash` of its predecessor as its `previousHash`.
- The first record uses the defined **genesis value** as its `previousHash` (OD-04 — PENDING: exact value not yet approved).
- If any record's stored fields are changed after persistence, recomputing its `contentHash` from those fields will produce a different value, breaking the chain at that point.
- Verification walks the chain in order, recomputes each `contentHash`, and checks that each `previousHash` equals the previous record's stored `contentHash`.

**Hash algorithm:** PENDING ENGINEERING DECISION — OD-02 (SHA-256 or SHA-3-256 under consideration).

**Genesis value:** PENDING ENGINEERING DECISION — OD-04.

---

## 8. Canonicalisation and Hashing

Hashing requires a deterministic, reproducible byte sequence as input. The same event fields must always produce the same hash regardless of runtime environment or JVM version.

**Fields to include in `contentHash` computation:**

| Field | Notes |
|-------|-------|
| `eventType` | String, included as-is |
| `actorId` | String, included as-is |
| `resourceType` | String, included as-is |
| `resourceId` | String, included as-is |
| `payload` | Structured — serialisation strategy PENDING (OD-03) |
| `timestamp` | Representation format PENDING — ISO-8601 UTC recommended (OD-03) |

Fields **not** included: `id`, `contentHash`, `previousHash` (these are derived, not source content).

**Open issues — PENDING DESIGN DECISION (OD-03):**

- Serialisation format: sorted-key JSON vs. field-concatenation with separator.
- Null/absent payload handling: empty string, `null` literal, or omit field.
- Timestamp format: must be canonical (e.g., `2026-08-21T10:00:00.000Z`).
- Character encoding: UTF-8 enforced.

No canonicalisation strategy is finalised until OD-03 is approved.

---

## 9. Concurrency and Ordering

**The problem:** Two requests arriving simultaneously may both read the same "last record" as their predecessor, resulting in two records that both claim the same `previousHash`. This creates a fork in the chain rather than a linear sequence.

```
  Record N  ←── both R(N+1) and R(N+2) try to set previousHash = contentHash(N)
```

This would mean the chain cannot be linearly verified.

**Architectural strategies under consideration (OD-06 — PENDING):**

| Strategy | Description | Trade-off |
|----------|-------------|-----------|
| Serialisable transaction | Use `SERIALIZABLE` isolation; conflicting transactions retry | Simple; adds retry complexity |
| Pessimistic row lock | Lock the last record row during the append operation | Prevents forks; reduces throughput |
| Sequence table / advisory lock | A separate mechanism serialises appends | More explicit; single point of serialisation |
| Application-level mutex | JVM-level lock on the append path | Not suitable for multi-instance deployment |

**Final concurrency strategy: PENDING ENGINEERING DECISION (OD-06).**

The chosen strategy must also define the **ordering field** (OD-05) — whether chain order is determined by timestamp, a monotonic sequence number, or insertion order.

---

## 10. Transaction Boundaries

All of the following must occur within a single database transaction for each write:

1. Read the most recent record to obtain its `contentHash` (becomes the new record's `previousHash`).
2. Compute the `contentHash` of the new record (application layer, within transaction scope).
3. Insert the new record with both `contentHash` and `previousHash`.

**Why a single transaction:**  
If the previous-record read and the INSERT are in separate transactions, a concurrent write could insert a record between the read and the INSERT, silently breaking the chain.

**Spring `@Transactional` boundary:** Expected to be placed at the service layer method responsible for the write operation (OD-07 — PENDING final approval of strategy).

---

## 11. Query Architecture

```
Client
  │
  ▼  GET /audit?actorId=X&from=T1&to=T2&page=0&size=20
Controller
  │  Parse and validate query parameters
  │  Return 400 if invalid
  ▼
Security Layer
  │  Authenticate and authorise read access
  ▼
Query Service
  │  Build filter specification from validated parameters
  ▼
Repository
  │  Execute filtered, paginated, ordered database query
  ▼
PostgreSQL
  ▼
Controller
  │  Map results to paginated response DTO
  ▼
Client  ← 200 OK  { content: [...], page, size, totalElements }
```

**Supported filters:** `actorId`, `resourceType`, `resourceId`, `eventType`, `from` (timestamp, inclusive), `to` (timestamp, inclusive), combined.

**Pagination:** Required — strategy (offset-based vs. cursor-based) PENDING (OD-13).

**Ordering:** Must be deterministic across calls. Field and direction PENDING (OD-14 — timestamp ASC or sequence ASC under consideration).

---

## 12. Verification Architecture

```
Client
  │
  ▼  GET /audit/verify
Security Layer
  │  Authenticate and authorise (admin role required)
  ▼
Verification Service
  │
  ├─ Read all records in defined chain order
  │
  ├─ For each record:
  │     Recompute contentHash from stored auditable fields
  │     Compare to stored contentHash
  │     Verify stored previousHash equals contentHash of preceding record
  │
  ├─ On first mismatch:
  │     Record ID of first inconsistent record
  │     Record violation type (content mismatch / previousHash mismatch)
  │     Stop walk
  │
  └─ Return result
  ▼
Controller
  ▼
Client  ← 200 OK  { valid: true/false, firstInconsistentRecord: ..., violationType: ... }
```

**Direct database tampering detection:**  
If a record's fields are modified outside the application, recomputing its `contentHash` will not match its stored `contentHash`. The verification walk will detect this at the tampered record.

**Not yet implemented.** This is an architectural description only.

---

## 13. Scenario B Architecture

### 13.1 Retention

The retention service identifies records older than the configured retention window. It archives or soft-deletes those records via the retention mechanism (OD-08 — PENDING).

**Architectural concern with verification:**  
If an archived record is removed from the primary chain, the verification walk will encounter a gap — a record whose predecessor no longer exists. The verification service must be designed to recognise archived records as a legitimate state and not report them as tampering. The exact strategy depends on the retention mechanism chosen (OD-08).

### 13.2 Redaction

Redaction modifies the `payload` of a stored record to remove or mask sensitive fields.

**Architectural concern:**  
The original `contentHash` was computed over the unredacted content. After redaction, recomputing the `contentHash` from the stored fields will produce a different value. This means the verification walk will report the redacted record as tampered.

This is an inherent trade-off, not a bug. The architecture must explicitly define what happens during verification when a redacted record is encountered (OD-09 — PENDING). Options include:

- Storing a redaction marker alongside the record so verification can skip or specially handle it.
- Storing both the original hash and a "redacted-content hash" so the chain can still be partially verified.

**The trade-off must be documented and approved before implementation** (REQ-B-008).

### 13.3 Export

The bulk export component produces a self-contained file containing:

- All selected audit records (filtered by `resourceId` or `actorId`).
- All chain metadata (contentHash, previousHash, chain anchor).
- Enough information to allow offline verification without the running service.

**Export format:** PENDING (OD-10 — JSON lines, CSV, or signed JSON document under consideration).

The verification logic used for online `GET /audit/verify` must have an offline equivalent that can be applied to the export file.

---

## 14. Scenario C Architecture

Scenario C is intentionally ambiguous. The assessment requires that ambiguous requirements be clarified before design and implementation.

**No Scenario C architecture is defined at this stage.**

The expected process:

```
Ambiguous requirement
  │
  ▼
Clarification questions answered (see REQUIREMENT-ANALYSIS.md §6)
  │
  ▼
Normalised requirement statement
  │
  ▼
Architecture update to this document
  │
  ▼
Implementation
  │
  ▼
Test evidence
```

Scenario C components, endpoints, and data model extensions will be added to this document after clarification is complete.

---

## 15. Authentication Architecture

All protected endpoints must require the caller to be authenticated before any business logic is executed. The security layer intercepts requests before they reach a controller.

**Architectural responsibilities:**
- Validate the credential or token on every protected request.
- Extract and expose the authenticated identity (user ID, role) to downstream layers.
- Reject unauthenticated requests with 401 before any data access.
- Never log raw credentials or tokens.
- Token/credential validation must happen server-side; expired or tampered tokens must be rejected.

**Authentication mechanism:** PENDING ENGINEERING DECISION (OD-11 — JWT self-signed, OAuth2, or API key under consideration).

No authentication mechanism is implemented or assumed in this document.

---

## 16. Authorisation Architecture

Access to API operations is controlled by the caller's assigned role.

**Proposed roles** (subject to approval — OD-12):

| Role | Intended Access |
|------|----------------|
| `SERVICE` | Write audit events (`POST /audit`) |
| `AUDITOR` | Read audit events (`GET /audit`) |
| `ADMIN` | All operations including verify, redact, export |

**Endpoint authorisation mapping (proposed — PENDING ENGINEERING REVIEW):**

| Endpoint | Minimum Role |
|----------|-------------|
| `POST /audit` | SERVICE |
| `GET /audit` | AUDITOR |
| `GET /audit/verify` | ADMIN |
| Redaction endpoint | ADMIN |
| Bulk export endpoint | ADMIN |

**Final authorisation matrix: PENDING ENGINEERING REVIEW (OD-12).**

No authorisation is implemented or finalised in this document.

---

## 17. Error Handling Architecture

All errors must produce a consistent, safe response structure. No error response may expose stack traces, internal class names, database schema details, or file paths.

**Proposed error response shape:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "eventType must not be blank",
  "timestamp": "2026-08-21T10:00:00Z"
}
```

**Error categories and expected HTTP status:**

| Category | HTTP Status |
|----------|-------------|
| Missing or invalid input | 400 Bad Request |
| Unauthenticated | 401 Unauthorized |
| Authorised but forbidden | 403 Forbidden |
| Resource not found | 404 Not Found |
| Chain integrity violation detected | 200 OK with `valid: false` (not an error — expected result) |
| Unexpected application error | 500 Internal Server Error (safe message only) |
| Database failure | 500 Internal Server Error (safe message only) |

A global exception handler (e.g., `@RestControllerAdvice`) will intercept all unhandled exceptions and map them to the consistent response structure.

Not yet implemented.

---

## 18. Observability

**Application logs** must record:

- Service startup and shutdown.
- Each audit event write (record ID, eventType — no sensitive payload content).
- Each verification run (result: valid/invalid, first inconsistent record ID if invalid).
- Authentication failures (no credential details in the log).
- Authorisation denials (user ID, requested operation).
- Database errors.
- Retention and redaction operations (record ID, operation type).

**Principles:**
- Sensitive payload data must not be logged unless explicitly required and risk-assessed.
- Log levels: INFO for normal operations, WARN for business-rule rejections, ERROR for unexpected failures.
- Request/correlation IDs should be propagated through log entries for traceability (OD pending — not listed as a formal open decision but noted here as an engineering concern).

Not yet implemented.

---

## 19. Security Boundaries

```
Public (no authentication required):
  ─ Health check endpoint (if provided)
  ─ Actuator (scoped to non-sensitive endpoints only, if used)

Protected (authentication required):
  ─ POST /audit           (role: SERVICE)
  ─ GET  /audit           (role: AUDITOR)
  ─ GET  /audit/verify    (role: ADMIN)
  ─ Redaction endpoint    (role: ADMIN)
  ─ Bulk export endpoint  (role: ADMIN)

Database boundary:
  ─ The application is the sole authorised writer.
  ─ Direct database modification is detectable via verification but cannot be
    prevented at the application layer.
  ─ No update or delete of audit records is exposed through the application API.

Administrative operations:
  ─ Verification, redaction, and export are elevated-privilege operations.
  ─ These must not be accessible to SERVICE or AUDITOR roles.

Sensitive data:
  ─ Payload may contain PII. Structured redaction (Scenario B) addresses
    specific field removal, but full data-lifecycle governance is outside
    assessment scope.
```

---

## 20. Deployment Architecture

**Runtime components:**

```
Audit Log Service (Spring Boot JAR / container)
  │
  ▼
PostgreSQL (database server / container)
```

**Development environment:**

| Component | Version / Choice |
|-----------|-----------------|
| Java | 21 (approved — AI-001) |
| Spring Boot | 3.3.4 (current baseline) |
| Build tool | Maven |
| Database | PostgreSQL |
| Containerisation | Docker — PENDING decision on whether to include |
| CI | PENDING decision on whether to include for assessment scope |

Docker and CI configuration will not be created in this step. If selected, they will be added in a later step.

---

## 21. Architecture Decisions

| ID | Decision | Status | Reason |
|----|----------|--------|--------|
| AD-01 | Java 21 as the project runtime | **APPROVED** | Engineer decision — Java 25 rejected (AI-001) |
| AD-02 | Spring Boot modular monolith — no microservices | **APPROVED** | Appropriate for assessment scope; no requirement for distributed architecture |
| AD-03 | PostgreSQL for persistence | **APPROVED** | Assessment specifies PostgreSQL |
| AD-04 | Service layer owns hash computation and chain logic | **APPROVED** | Testability (NFR-004): business logic must be unit-testable independent of the database |
| AD-05 | Hash algorithm | **OPEN** | OD-02 — SHA-256 or SHA-3-256 under consideration |
| AD-06 | Canonical representation for contentHash | **OPEN** | OD-03 — sorted-key JSON vs. concatenation |
| AD-07 | Genesis value | **OPEN** | OD-04 — fixed string, zero-hash, or null |
| AD-08 | Chain ordering field | **OPEN** | OD-05 — timestamp vs. sequence number |
| AD-09 | Concurrency strategy for chain writes | **OPEN** | OD-06 |
| AD-10 | Retention mechanism | **OPEN** | OD-08 — hard delete with archive vs. soft-delete |
| AD-11 | Redaction mechanism and verify behaviour | **OPEN** | OD-09 |
| AD-12 | Export format | **OPEN** | OD-10 |
| AD-13 | Authentication mechanism | **OPEN** | OD-11 |
| AD-14 | Authorisation matrix | **OPEN** | OD-12 |
| AD-15 | Pagination strategy | **OPEN** | OD-13 |
| AD-16 | Query ordering field and direction | **OPEN** | OD-14 |
| AD-17 | Record ID type | **OPEN** | OD-15 — UUID vs. BIGSERIAL |

---

## 22. Architecture Risks

| Risk | Description | Status |
|------|-------------|--------|
| Concurrent writes | Two simultaneous writes may fork the hash chain | Open — OD-06 |
| Chain ordering ambiguity | If ordering relies on timestamp with low resolution, ties create non-determinism | Open — OD-05 |
| Deterministic hashing | Any non-determinism in canonical representation breaks verification portability | Open — OD-03 |
| Retention vs. verification | Deleting or archiving records creates gaps the verifier must handle | Open — OD-08 |
| Redaction vs. tamper evidence | Redacted content cannot be reverified against the original hash | Known trade-off — OD-09 |
| Export verification | Export must be truly self-contained; verification must not require the live service | Open — OD-10 |
| Privileged database access | DBAs can modify records directly; application cannot prevent this | Accepted — detected by verify |
| Large-chain verification | Full chain walk may be slow as the record count grows | Low risk for assessment; no SLA defined |
| Sensitive payload exposure | Payload may contain PII; redaction mitigates but does not fully address | Noted — Scenario B partial mitigation |

---

## 23. Requirement Traceability

| Requirement | Architecture Component |
|-------------|----------------------|
| REQ-A-001–012 (Write) | Controller → Audit Service → Persistence → PostgreSQL |
| REQ-A-013–023 (Query) | Controller → Query Service → Repository → PostgreSQL |
| REQ-A-024–030 (Hash Chain) | Audit Service (hash computation, chain linking, transaction boundary) |
| REQ-A-031–036 (Verification) | Controller → Verification Service → Repository → PostgreSQL |
| REQ-B-001–004 (Retention) | Retention Service → Repository → PostgreSQL |
| REQ-B-005–008 (Redaction) | Redaction Service → Repository → PostgreSQL |
| REQ-B-009–013 (Export) | Export Service → Repository → PostgreSQL → File output |
| REQ-C-* | PENDING CLARIFICATION |
| SEC-001–015 | Security Layer (authentication filter, authorisation interceptor) |
| DATA-* | Entity + DTO + Repository |
| NFR-001 (security) | Security Layer |
| NFR-002 (reliability) | Transactional write in Audit Service |
| NFR-004 (testability) | Service layer independent of persistence |
| NFR-006 (determinism) | Canonicalisation strategy (OD-03) |
| NFR-007 (observability) | Application logging across all layers |
| TEST-001–007 | All layers (testable by design) |

---

## 24. Architecture Validation Checklist

- [ ] Requirements covered — all REQ-A/B/C, SEC, DATA, NFR IDs mapped to components
- [ ] Scenario A architecture defined — write, query, hash chain, verification flows
- [ ] Scenario B architecture defined — retention, redaction, export components and trade-offs
- [ ] Scenario C ambiguity preserved — no Scenario C architecture designed prematurely
- [ ] Security boundary defined — public vs. protected, authentication layer, authorisation roles
- [ ] Hash-chain architecture defined — chain model, genesis value, verification walk
- [ ] Concurrency issue identified — fork risk documented, strategy pending
- [ ] Transaction boundaries identified — single-transaction write requirement stated
- [ ] Query architecture defined — filters, pagination, ordering
- [ ] Verification architecture defined — walk, recompute, detect tamper, first inconsistent record
- [ ] Error handling considered — consistent safe response structure defined
- [ ] Observability considered — log events and principles defined
- [ ] Deployment architecture defined — Spring Boot + PostgreSQL runtime documented
- [ ] Open decisions identified — 13 open decisions carried forward from REQUIREMENT-ANALYSIS.md
