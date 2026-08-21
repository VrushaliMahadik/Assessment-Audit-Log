# Final Engineering Summary

**Project:** Audit Log Service  
**Engineer:** Vrushali Mahadik  
**Runtime:** Java 21 / Spring Boot 3.3.4  
**Database:** PostgreSQL 16  
**Date:** 2026-08-21

## 1. Project Overview

The project is a backend audit-log service that accepts structured audit events, persists them in an append-only PostgreSQL chain, exposes deterministic query and verification APIs, and implements retention, structured redaction, bulk export, and client-account access recording.

## 2. Requirement Coverage

### Scenario A

Implemented:

- Event creation and persistence
- Retrieval by ID and safe not-found handling
- Filtering by actor, resource, event type, and time range
- Pagination and deterministic sequence-number ordering
- Server-assigned timestamps
- SHA-256 content hashes and previous-hash links
- Genesis handling, chain verification, and direct database tamper detection

### Scenario B

Implemented:

- Configurable retention with soft archival through `archived_at`
- Structured payload redaction with redaction metadata
- Documented redaction/hash-integrity trade-off
- Bulk export by actor or resource with chain metadata
- ADMIN-only retention, redaction, verification, and export operations

### Scenario C

Implemented using the conservative decisions documented in `docs/SCENARIO-C-DESIGN.md`:

- Successful READ and WRITE client-account access recording
- Dedicated `POST /api/v1/audit/client-account-access` endpoint
- Fixed `CLIENT_ACCOUNT_ACCESS` event type and `CLIENT_ACCOUNT` resource type
- Existing audit chain, query, retention, redaction, export, and security boundaries reused
- Failed-access interception and external reporting audiences remain out of scope

## 3. Architecture

The service is a Spring Boot modular monolith using:

```text
Controller -> Security -> Service -> Repository -> PostgreSQL
```

- Controllers map HTTP requests and apply DTO validation.
- Spring Security provides stateless OAuth2/JWT resource-server authentication and authority checks.
- `AuditEventService` owns chain append, hashing, verification, retention, redaction, export, and Scenario C mapping.
- `AuditEventRepository` uses Spring JDBC and parameterized SQL through `JdbcTemplate`.
- `HashService` provides deterministic canonicalization and SHA-256 hashing.

## 4. Database

PostgreSQL is managed through Flyway migrations:

- `V1__create_audit_event_table.sql` creates the audit-event table, UUID primary key, BIGSERIAL sequence ordering, chain fields, JSONB payload, timestamps, constraints, and query indexes.
- `V2__add_scenario_b_columns.sql` adds retention and redaction metadata and related indexes.

The application uses Spring JDBC, not Spring Data JPA/Hibernate. No Scenario C migration was required because the existing audit-event schema is sufficient.

## 5. Security

- OAuth2/JWT resource-server authentication is required for protected requests.
- Sessions are stateless and CSRF is disabled for the stateless API.
- `SERVICE` and `ADMIN` may create audit events and record Scenario C access.
- `AUDITOR` and `ADMIN` may query audit events.
- `ADMIN` is required for verification, retention execution, redaction, and export.
- Credentials and JWT configuration are supplied through environment-backed properties; no real secrets are committed.
- Error responses avoid stack traces, SQL details, credentials, and tokens.

## 6. Audit Integrity

- Content hashes use SHA-256.
- Canonical content uses sorted-key JSON over auditable event fields.
- The genesis value is 64 zero characters.
- `sequence_number ASC` is the authoritative chain order.
- PostgreSQL advisory-lock serialization prevents concurrent chain forks.
- Verification recomputes hashes and checks previous-hash links.
- Direct database tampering is detected by the verification endpoint.
- Redaction intentionally preserves the original content hash and documents the resulting verification trade-off.

## 7. Testing

Actual local results:

- **35 executable tests passed**
- **0 failures**
- **0 errors**
- **38 PostgreSQL/Testcontainers tests were not executed because Docker was unavailable**

The 38 unexecuted tests are 23 Scenario A tests, 9 database migration tests, and 6 Scenario C tests. The complete evidence is recorded in `docs/TESTING-EVIDENCE.md`.

## 8. Risks and Trade-offs

- PostgreSQL integration validation is environment-dependent because Testcontainers requires Docker.
- Redaction changes stored payload content after the original hash was computed; this is documented and visible to verification behavior.
- Full-chain verification is linear in the number of audit records.
- Application-layer append-only controls do not prevent a privileged database administrator from modifying data; verification detects such modification.
- The Scenario C interpretation is conservative and may need revision if the assessment owner supplies a more specific business contract.

## 9. Assumptions

- Scenario C access means successful READ or WRITE access to a `CLIENT_ACCOUNT` resource.
- Scenario C records are submitted through the dedicated API; failed-access interception is out of scope.
- Existing SERVICE, AUDITOR, and ADMIN authorities are sufficient.
- Scenario C reuses Scenario B retention and redaction behavior.
- Existing actor/resource/event/time filters are sufficient for Scenario C reporting.
- PostgreSQL and Docker/Testcontainers remain the database validation path.

## 10. Limitations

Docker was unavailable in the local environment. Therefore the 38 PostgreSQL/Testcontainers tests were not executed and must not be interpreted as passed. The non-Docker unit, service, security, hash, context, and available validation tests passed.

## 11. AI-Assisted Development

AI-assisted development history is recorded in `ai/ai-usage.md`. The log contains the recorded entries `AI-001` and `AI-003` through `AI-018`; the missing `AI-002` is not invented or renumbered.

## 12. Final Status

The application implementation is complete for the documented Scenario A, Scenario B, and conservative Scenario C scope. Local executable tests pass with zero failures and errors. PostgreSQL/Testcontainers validation remains the only material validation limitation because Docker was unavailable.
