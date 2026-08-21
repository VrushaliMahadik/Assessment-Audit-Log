# Testing Evidence

**Project:** Audit Log Service  
**Engineer:** Vrushali Mahadik  
**Status:** Scenario C remains blocked by unresolved requirements. The Maven suite has been executed for regression evidence; Docker-backed integration tests remain environment-dependent.

---

## Purpose

This document records actual validation evidence against the requirements defined in `docs/REQUIREMENT-ANALYSIS.md`.

**Rules:**
- Every entry represents a test that has been or will be executed against the real system.
- No result is recorded until the test has actually been run.
- `NOT RUN` means the test has not yet been executed — it does not imply failure or success.
- `PASS` may only be recorded when the test was executed and the actual result matched the expected result.
- `FAIL` must be recorded honestly when the test was executed and the actual result did not match.
- Results must not be invented, assumed, or copied from similar tests.

## Executed Regression Evidence — 2026-08-21

| Command | Actual result | Evidence |
|---------|---------------|----------|
| `./mvnw clean test -q` | PASS: 29 tests executed, 0 failures, 0 errors; 32 Docker-dependent integration tests skipped because Docker is unavailable | Maven Surefire XML reports under `target/surefire-reports/` |
| Scenario A regression | PASS for the 0 non-container tests in the class; 23 PostgreSQL-backed tests skipped because Docker is unavailable | `ScenarioAIntegrationTest` Surefire report |
| Scenario B regression | PASS: 3 tests, 0 failures, 0 errors | `ScenarioBServiceTest` Surefire report |
| Authentication and authorization regression | PASS: 16 tests, 0 failures, 0 errors | `AuthenticationIntegrationTest` and `AuthorizationIntegrationTest` Surefire reports |
| Database migration integration | 9 tests skipped because Docker is unavailable; no failures or errors | `DatabaseMigrationIntegrationTest` Surefire report |

Scenario C functional tests were not executed because the approved requirement remains PENDING CLARIFICATION. Adding executable tests would require inventing the behavior they are intended to verify. The clarification gate and normalized boundary are documented in `docs/SCENARIO-C-DESIGN.md`.

---

## Test Evidence Record Format

For every completed test execution, record:

| Field | Description |
|-------|-------------|
| Requirement ID | The `REQ-*`, `SEC-*`, `NFR-*`, or `TEST-*` ID from REQUIREMENT-ANALYSIS.md |
| Test ID | Unique test identifier from the inventory below |
| Test type | Unit / Integration / E2E / Security / Manual |
| Scenario | Human-readable scenario description |
| Preconditions | State required before the test runs |
| Test steps | What was done |
| Expected result | What should happen |
| Actual result | What actually happened — fill in after execution |
| Status | NOT RUN / PASS / FAIL |
| Date | Date test was executed |
| Evidence / Reference | Log output, test report, screenshot, or test class name |
| Related Git commit | Commit at which the test was run |

---

## Test Case Inventory

| Test ID | Requirement ID | Scenario | Test Type | Description | Status |
|---------|----------------|----------|-----------|-------------|--------|
| T-A-001 | REQ-A-001, REQ-A-009 | Scenario A — Write | Integration | Valid audit event is created and persisted | NOT RUN |
| T-A-002 | REQ-A-002 | Scenario A — Write | Integration | Missing eventType returns 400 | NOT RUN |
| T-A-003 | REQ-A-003 | Scenario A — Write | Integration | Missing actorId returns 400 | NOT RUN |
| T-A-004 | REQ-A-004 | Scenario A — Write | Integration | Missing resourceType returns 400 | NOT RUN |
| T-A-005 | REQ-A-005 | Scenario A — Write | Integration | Missing resourceId returns 400 | NOT RUN |
| T-A-006 | REQ-A-006 | Scenario A — Write | Integration | payload is optional; omitting it succeeds | NOT RUN |
| T-A-007 | REQ-A-007 | Scenario A — Write | Integration | Timestamp is server-assigned | NOT RUN |
| T-A-008 | REQ-A-008 | Scenario A — Write | Integration | Blank required field returns 400 with descriptive message | NOT RUN |
| T-A-009 | REQ-A-010 | Scenario A — Hash | Unit | contentHash is computed and stored | NOT RUN |
| T-A-010 | REQ-A-010 | Scenario A — Hash | Unit | Same input always produces the same contentHash (determinism) | NOT RUN |
| T-A-011 | REQ-A-011 | Scenario A — Hash | Integration | previousHash of record N equals contentHash of record N-1 | NOT RUN |
| T-A-012 | REQ-A-026 | Scenario A — Hash | Integration | First record's previousHash equals the defined genesis value | NOT RUN |
| T-A-013 | REQ-A-012 | Scenario A — Write | Integration | No UPDATE endpoint exists for audit records | NOT RUN |
| T-A-014 | REQ-A-012 | Scenario A — Write | Integration | No DELETE endpoint exists for audit records | NOT RUN |
| T-A-015 | REQ-A-013, REQ-A-014 | Scenario A — Query | Integration | Filter by actorId returns only matching records | NOT RUN |
| T-A-016 | REQ-A-015 | Scenario A — Query | Integration | Filter by resourceType returns only matching records | NOT RUN |
| T-A-017 | REQ-A-016 | Scenario A — Query | Integration | Filter by resourceId returns only matching records | NOT RUN |
| T-A-018 | REQ-A-017 | Scenario A — Query | Integration | Filter by eventType returns only matching records | NOT RUN |
| T-A-019 | REQ-A-018 | Scenario A — Query | Integration | Filter by from timestamp returns records at or after boundary | NOT RUN |
| T-A-020 | REQ-A-019 | Scenario A — Query | Integration | Filter by to timestamp returns records at or before boundary | NOT RUN |
| T-A-021 | REQ-A-020 | Scenario A — Query | Integration | Combined filters narrow results correctly | NOT RUN |
| T-A-022 | REQ-A-021 | Scenario A — Query | Integration | Invalid query parameter format returns 400 | NOT RUN |
| T-A-023 | REQ-A-022 | Scenario A — Query | Integration | Paginated response returns correct page and size | NOT RUN |
| T-A-024 | REQ-A-023 | Scenario A — Query | Integration | Results are returned in deterministic order across multiple calls | NOT RUN |
| T-A-025 | REQ-A-031, REQ-A-032, REQ-A-033 | Scenario A — Verify | Integration | Verify returns valid=true for an unmodified chain | NOT RUN |
| T-A-026 | REQ-A-033, REQ-A-034, REQ-A-035, REQ-A-036 | Scenario A — Tamper | Integration | Verify detects direct database modification | NOT RUN |
| T-A-027 | REQ-A-034 | Scenario A — Tamper | Integration | First inconsistent record is correctly identified | NOT RUN |
| T-A-028 | REQ-A-035 | Scenario A — Tamper | Integration | Violation type is included in verify response | NOT RUN |
| T-B-001 | REQ-B-001, REQ-B-004 | Scenario B — Retention | Integration | Records outside retention window are archived/soft-deleted | NOT RUN |
| T-B-002 | REQ-B-003 | Scenario B — Retention | Integration | Verify returns valid=true after legitimate archival | NOT RUN |
| T-B-003 | REQ-B-005, REQ-B-006 | Scenario B — Redaction | Integration | Sensitive payload fields are redacted | NOT RUN |
| T-B-004 | REQ-B-007 | Scenario B — Redaction | Integration | Verify behaviour after redaction matches documented specification | NOT RUN |
| T-B-005 | REQ-B-009 | Scenario B — Export | Integration | Bulk export by resourceId returns correct records | NOT RUN |
| T-B-006 | REQ-B-009 | Scenario B — Export | Integration | Bulk export by actorId returns correct records | NOT RUN |
| T-B-007 | REQ-B-010, REQ-B-011, REQ-B-013 | Scenario B — Export | Manual | Exported file is independently verifiable without the running service | NOT RUN |
| T-C-001 | REQ-C-* | Scenario C | TBD | Scenario C tests — PENDING CLARIFICATION | NOT RUN |
| T-SEC-001 | SEC-001, SEC-007 | Authentication | Integration | Request with valid credentials to POST /audit succeeds | NOT RUN |
| T-SEC-002 | SEC-007 | Authentication | Integration | Request without credentials returns 401 | NOT RUN |
| T-SEC-003 | SEC-007 | Authentication | Integration | Request with invalid credentials returns 401 | NOT RUN |
| T-SEC-004 | SEC-010 | Authentication | Integration | Request with expired token returns 401 | NOT RUN |
| T-SEC-005 | SEC-010 | Authentication | Integration | Request with malformed token returns 401 | NOT RUN |
| T-SEC-006 | SEC-002, SEC-008 | Authorization | Integration | Role with read permission can access GET /audit | NOT RUN |
| T-SEC-007 | SEC-008 | Authorization | Integration | Role without write permission is denied POST /audit | NOT RUN |
| T-SEC-008 | SEC-003 | Authorization | Integration | Non-admin role is denied GET /audit/verify | NOT RUN |
| T-SEC-009 | SEC-004 | Authorization | Integration | Non-admin role is denied bulk export | NOT RUN |
| T-SEC-010 | SEC-005 | Authorization | Integration | Non-admin role is denied redaction | NOT RUN |
| T-SEC-011 | SEC-011 | Security | Integration | Oversized payload returns 400 or 413 | NOT RUN |
| T-SEC-012 | SEC-012, SEC-014 | Security | Integration | Unauthorized request error body contains no sensitive internal detail | NOT RUN |
| T-SEC-013 | SEC-014 | Security | Integration | Server error response does not expose stack trace | NOT RUN |
| T-SEC-014 | SEC-009, SEC-015 | Security | Manual | No credentials or secrets present in source code or test fixtures | NOT RUN |
| T-QG-001 | TEST-001 | Quality Gate | Build | `mvn clean package` completes with BUILD SUCCESS | NOT RUN |
| T-QG-002 | TEST-002 | Quality Gate | Unit | All unit tests pass | NOT RUN |
| T-QG-003 | TEST-003 | Quality Gate | Integration | All integration tests pass | NOT RUN |
| T-QG-004 | TEST-003 | Quality Gate | E2E | End-to-end scenario tests pass | NOT RUN |
| T-QG-005 | TEST-005 | Quality Gate | Static Analysis | Static analysis produces no critical findings | NOT RUN |
| T-QG-006 | TEST-006 | Quality Gate | Security | No secrets found in source code | NOT RUN |
| T-QG-007 | NFR-009 | Quality Gate | Build | Application starts from a clean checkout | NOT RUN |

---

## Scenario A Testing

### T-A-001 — Valid Audit Event Creation

| Field | Value |
|-------|-------|
| Requirement ID | REQ-A-001, REQ-A-009 |
| Test type | Integration |
| Preconditions | Application running; database available |
| Test steps | Send POST /audit with all required fields |
| Expected result | HTTP 201; body contains id, timestamp, contentHash, previousHash |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

### T-A-002 through T-A-008 — Required Field Validation

| Field | Value |
|-------|-------|
| Requirement ID | REQ-A-002–008 |
| Test type | Integration |
| Preconditions | Application running |
| Test steps | Send POST /audit omitting each required field in turn; also send with blank values |
| Expected result | HTTP 400 for each missing or blank required field |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

### T-A-009 / T-A-010 — Hash Generation

| Field | Value |
|-------|-------|
| Requirement ID | REQ-A-010 |
| Test type | Unit |
| Preconditions | Hash service / component available for unit testing |
| Test steps | (1) Invoke hash computation with known inputs; (2) assert output matches expected hash; (3) invoke again with same inputs and assert identical output |
| Expected result | Consistent, deterministic contentHash |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

### T-A-011 / T-A-012 — Hash Chain Creation

| Field | Value |
|-------|-------|
| Requirement ID | REQ-A-011, REQ-A-026 |
| Test type | Integration |
| Preconditions | Application running; database available |
| Test steps | (1) Create record 1; (2) assert previousHash equals genesis value; (3) create record 2; (4) assert record 2's previousHash equals record 1's contentHash |
| Expected result | Chain links are correct |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

### T-A-015 through T-A-024 — Query and Filtering

| Field | Value |
|-------|-------|
| Requirement ID | REQ-A-013–023 |
| Test type | Integration |
| Preconditions | Application running; seeded test data covering all filter dimensions |
| Test steps | For each filter: (1) seed records; (2) query with filter; (3) assert only matching records returned |
| Expected result | Correct records returned; correct pagination; deterministic ordering |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

### T-A-025 — Chain Verification (Clean)

| Field | Value |
|-------|-------|
| Requirement ID | REQ-A-031, REQ-A-032, REQ-A-033 |
| Test type | Integration |
| Preconditions | Application running; multiple records created via API only |
| Test steps | (1) Create N records via POST /audit; (2) call GET /audit/verify |
| Expected result | `{ "valid": true }` |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

---

## Scenario A — Tamper-Evidence Test

This is a dedicated evidence section for the most critical test in the assessment.

### T-A-026 — Direct Database Tamper Detection

| Field | Value |
|-------|-------|
| Requirement ID | REQ-A-033, REQ-A-034, REQ-A-035, REQ-A-036 |
| Test type | Integration / Manual |
| Preconditions | Application running; at least 5 records created via the API |

**Test Steps:**

1. Create at least 5 audit records via `POST /audit`.
2. Call `GET /audit/verify` — confirm `valid: true`.
3. Identify a record in the middle of the chain (e.g., record 3 of 5).
4. Using a direct database connection, update a field on that record (e.g., change `actorId`).
5. Do NOT update `contentHash` or `previousHash`.
6. Call `GET /audit/verify` again.
7. Record the full response body.

| Expected result | `valid: false`; `firstInconsistentRecord` identifies the tampered record; `violationType` populated |
|---|---|
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

---

## Scenario B Testing

### T-B-001 / T-B-002 — Retention and Archival

| Field | Value |
|-------|-------|
| Requirement ID | REQ-B-001, REQ-B-003, REQ-B-004 |
| Test type | Integration |
| Preconditions | Retention period configurable; application running |
| Test steps | (1) Create records; (2) advance time or modify retention config; (3) trigger retention job; (4) assert records outside window are archived/soft-deleted; (5) call verify and assert valid=true |
| Expected result | Archived records handled correctly; no false chain failure |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

### T-B-003 / T-B-004 — Structured Redaction

| Field | Value |
|-------|-------|
| Requirement ID | REQ-B-005, REQ-B-006, REQ-B-007 |
| Test type | Integration |
| Preconditions | Application running; a record with a payload containing sensitive fields |
| Test steps | (1) Create record with sensitive payload; (2) call redaction endpoint; (3) assert sensitive fields are redacted in stored record; (4) call verify and compare result against documented specification |
| Expected result | Fields redacted; verify behaviour matches documented specification (PENDING) |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

### T-B-005 through T-B-007 — Bulk Export

| Field | Value |
|-------|-------|
| Requirement ID | REQ-B-009, REQ-B-010, REQ-B-011, REQ-B-013 |
| Test type | Integration / Manual |
| Preconditions | Application running; records exist for a known resourceId and actorId |
| Test steps | (1) Request export by resourceId; (2) assert export contains only matching records; (3) assert chain metadata is included; (4) perform offline verification using only the export file |
| Expected result | Correct, self-contained, independently verifiable export |
| Actual result | _[To be completed after execution]_ |
| Status | NOT RUN |
| Date | — |
| Evidence | — |
| Related commit | — |

---

## Scenario C Testing

Scenario C is finalized as successful READ/WRITE client-account access recording through `POST /api/v1/audit/client-account-access`. The endpoint fixes `eventType=CLIENT_ACCOUNT_ACCESS`, `resourceType=CLIENT_ACCOUNT`, and `outcome=SUCCESS`, while reusing existing authentication, authorization, query, retention, redaction, export, and hash-chain behavior.

| Test ID | Requirement ID | Description | Status |
|---------|----------------|-------------|--------|
| T-C-001 | REQ-C-* | Service mapping fixes event/resource/access semantics and appends to the existing chain | PASS |
| T-C-002 | REQ-C-* | SERVICE can record access and AUDITOR is denied write access | PASS |
| T-C-003 | REQ-C-* | PostgreSQL API tests cover READ/WRITE recording, validation, authentication, query, and verification | NOT RUN — Docker unavailable |

### Scenario C Actual Evidence — 2026-08-21

| Test | Expected result | Actual result | Status | Evidence |
|------|-----------------|---------------|--------|----------|
| `ScenarioCServiceTest` | Scenario C request maps to fixed event/resource values and existing chain fields | 1 test passed; 0 failures/errors | PASS | `target/surefire-reports/TEST-com.vrushali.auditlog.service.ScenarioCServiceTest.xml` |
| `AuthorizationIntegrationTest` Scenario C cases | SERVICE is allowed and AUDITOR is denied | 13 authorization tests passed; 0 failures/errors | PASS | `target/surefire-reports/TEST-com.vrushali.auditlog.security.AuthorizationIntegrationTest.xml` |
| `ScenarioCIntegrationTest` | PostgreSQL-backed happy path, validation, authentication, query, and verification tests pass | 6 tests skipped because Docker is unavailable; 0 failures/errors | NOT RUN | `target/surefire-reports/TEST-com.vrushali.auditlog.ScenarioCIntegrationTest.xml` |

---

## Authentication Testing

| Test ID | Requirement ID | Scenario | Expected Result | Status |
|---------|----------------|----------|----------------|--------|
| T-SEC-001 | SEC-001, SEC-007 | Valid credentials to protected endpoint | 200 / 201 response | NOT RUN |
| T-SEC-002 | SEC-007 | No credentials provided | 401 Unauthorized | NOT RUN |
| T-SEC-003 | SEC-007 | Invalid credentials | 401 Unauthorized | NOT RUN |
| T-SEC-004 | SEC-010 | Expired token | 401 Unauthorized | NOT RUN |
| T-SEC-005 | SEC-010 | Malformed / tampered token | 401 Unauthorized | NOT RUN |

---

## Authorization Testing

| Test ID | Requirement ID | Scenario | Expected Result | Status |
|---------|----------------|----------|----------------|--------|
| T-SEC-006 | SEC-002, SEC-008 | Role with read permission — GET /audit | 200 | NOT RUN |
| T-SEC-007 | SEC-008 | Role without write permission — POST /audit | 403 Forbidden | NOT RUN |
| T-SEC-008 | SEC-003 | Non-admin role — GET /audit/verify | 403 Forbidden | NOT RUN |
| T-SEC-009 | SEC-004 | Non-admin role — bulk export | 403 Forbidden | NOT RUN |
| T-SEC-010 | SEC-005 | Non-admin role — redaction | 403 Forbidden | NOT RUN |

---

## Security Testing

| Test ID | Requirement ID | Scenario | Expected Result | Status |
|---------|----------------|----------|----------------|--------|
| T-SEC-011 | SEC-011 | Oversized payload submitted | 400 or 413; no server error | NOT RUN |
| T-SEC-012 | SEC-012, SEC-014 | Unauthorized request — inspect response body | No internal detail, stack trace, or schema info | NOT RUN |
| T-SEC-013 | SEC-014 | Force a server error — inspect response body | Generic error message; no stack trace | NOT RUN |
| T-SEC-014 | SEC-009, SEC-015 | Inspect source code and test fixtures for secrets | No credentials, API keys, or passwords committed | NOT RUN |

---

## Quality Gate Evidence

| Test ID | Gate | Description | Command / Method | Status |
|---------|------|-------------|-----------------|--------|
| T-QG-001 | Build | Maven build succeeds | `mvn clean package` | NOT RUN |
| T-QG-002 | Unit tests | All unit tests pass | `mvn test` | NOT RUN |
| T-QG-003 | Integration tests | All integration tests pass | `mvn verify` | NOT RUN |
| T-QG-004 | E2E tests | End-to-end scenario tests pass | TBD | NOT RUN |
| T-QG-005 | Static analysis | No critical findings | Tool TBD (e.g., Checkstyle, SpotBugs) | NOT RUN |
| T-QG-006 | Secret scanning | No secrets in source | Manual / tool TBD | NOT RUN |
| T-QG-007 | Startup | Application starts from clean checkout | `mvn spring-boot:run` | NOT RUN |

---

## Tamper-Evidence Test — Dedicated Evidence Record

This section will hold the complete evidence trail for T-A-026 when executed.

### Pre-Execution Setup

- [ ] At least 5 records created via `POST /audit`.
- [ ] Initial `GET /audit/verify` confirms `valid: true`.
- [ ] Record IDs and contentHash values noted before tampering.

### Tamper Action

- [ ] Record selected for tampering: _[ID — to be filled in]_
- [ ] Field modified: _[to be filled in]_
- [ ] Old value: _[to be filled in]_
- [ ] New value: _[to be filled in]_
- [ ] contentHash and previousHash left unchanged: _[to be confirmed]_

### Post-Tamper Verification

- [ ] `GET /audit/verify` called after tampering.
- [ ] Full response body recorded below.

```json
[Response body — to be recorded after test execution]
```

### Result

| Field | Value |
|-------|-------|
| valid | _[to be recorded]_ |
| firstInconsistentRecord | _[to be recorded]_ |
| violationType | _[to be recorded]_ |
| Status | NOT RUN |
| Date executed | — |
| Evidence | — |
| Related commit | — |
