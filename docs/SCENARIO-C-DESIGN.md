# Scenario C Design

**Status:** Clarifications resolved by documented conservative assumptions; implementation complete  
**Date:** 2026-08-21

## Requirement

The approved requirement analysis states that Scenario C concerns audit logging for a specific access or activity context, but leaves its details open. This document resolves those details using the requested decision policy: prefer existing requirements and designs, choose the smallest compatible behavior, and document each assumption.

The controlling source is `docs/REQUIREMENT-ANALYSIS.md`, Section 6. The related architecture, API contract, database design, and testing evidence documents agree that Scenario C has no approved implementation contract yet.

## Ambiguity

The unresolved questions are:

- Whether access means read access, write access, or both.
- Whether successful access attempts, failed attempts, or both must be recorded.
- What constitutes client account data.
- Which actors are in scope.
- Which event types are required.
- Whether a regulator or external auditor needs visibility.
- Whether retention or redaction differs from Scenario A and Scenario B.
- Who may access reports and through which interface.
- Whether time-range and Scenario C-specific filters are required.
- Whether recording occurs through direct API calls, exported reports, or both.

## Possible Interpretations

1. Extend the existing event-ingestion API with a Scenario C event type.
2. Add an access-decision interceptor that records successful and failed accesses automatically.
3. Add a reporting endpoint over existing audit records.
4. Add a dedicated client-account audit model with separate retention and authorization rules.

None of these interpretations is approved by the repository documentation. Choosing one would invent business behavior and could create an incompatible API, schema, or security contract.

## Selected Interpretation

Scenario C records successful read and write access to client-account resources through a dedicated API endpoint. The endpoint reuses the existing audit-event chain, persistence model, query model, retention/redaction behavior, and JWT role boundaries.

Failed access attempts are not recorded because implementing that behavior would require an access-decision interceptor or integration with the protected client-account system, neither of which exists in the repository. Exported reports are outputs only and do not create new access events.

## Assumptions

- The existing Scenario A and Scenario B audit-event model remains the foundation for any future Scenario C work.
- Existing authentication, authorization, PostgreSQL schema, migrations, DTO conventions, and error handling will be reused after the requirement is approved.
- No new database structure, retention policy, redaction rule, or reporting permission is required by the selected interpretation.
- Existing Scenario A and Scenario B behavior must remain unchanged.

## Open Questions

The original questions are resolved for this implementation by the decisions below. The decisions are assumptions derived from the repository and must be revisited if the assessment owner supplies a more specific Scenario C contract.

## Normalized Requirement

> Scenario C shall record successful READ and WRITE access to a `CLIENT_ACCOUNT` resource through `POST /api/v1/audit/client-account-access`, using the existing append-only audit chain. The request identifies the actor, client-account resource, access type, and optional structured details. SERVICE and ADMIN authorities may record events; AUDITOR and ADMIN authorities may query them through the existing query API. Failed access interception, external reporting audiences, separate retention/redaction policies, and export-generated events are out of scope.

## Scope

### In Scope

- Preserve the approved ambiguity and open questions.
- Record the interpretation and implementation boundary in this design document.
- Define the future design inputs needed before coding.
- Record the selected behavior and its implementation consequences.

### Out of Scope

- Failed-access interception and integration with an external client-account system.
- Changes to Scenario A or Scenario B behavior.
- Changes to authentication or authorization.
- Scenario D or any future functionality.
- New external-audience reporting or separate Scenario C retention/redaction policy.

## Technical Design Gate

### Actors

SERVICE and ADMIN callers are authorized to submit access records. `actorId` identifies the actor or subject represented by the event; the authenticated caller remains constrained by the existing JWT authority rules. AUDITOR and ADMIN callers may query the resulting events.

### Workflow

```text
Clarify C-Q-01 through C-Q-12
  -> approve normalized requirement
  -> update requirement, architecture, API, and database documents
  -> implement the smallest approved extension
  -> add unit, API, authorization, and database tests
  -> run regression and Scenario C validation
```

### Components

No components are added. The future implementation should use the existing Controller -> Service -> Repository -> PostgreSQL path unless the approved requirement demonstrates that an interceptor or asynchronous integration is necessary.

### API Design

`POST /api/v1/audit/client-account-access` accepts `actorId`, `resourceId`, `accessType` (`READ` or `WRITE`), and optional structured `payload` details. The service fixes `eventType` to `CLIENT_ACCOUNT_ACCESS`, `resourceType` to `CLIENT_ACCOUNT`, and adds `outcome: SUCCESS` to the stored payload. It returns the existing `AuditEventResponse` with HTTP 201.

### Service Design

`AuditEventService.recordClientAccountAccess` validates the Scenario C request, maps it to the existing `CreateAuditEventRequest`, and delegates to the existing chain append path. This preserves advisory-lock serialization, timestamp assignment, hashing, and transaction behavior.

### Database Impact

No migration or schema change is justified. The required data scope, event types, filters, retention, and redaction behavior are unknown.

### Security and Authorization

No new authorization rule is added. The actors, reporting audience, and privilege level are unresolved. Existing protected endpoints remain unchanged.

### Transaction Considerations

No new transaction is introduced. Future transaction boundaries depend on whether Scenario C records direct API activity, security decisions, or report generation.

### Error Handling

No new error contract is defined. Validation and error responses must be specified after the required API or interception point is selected.

### Testing Strategy

Scenario C tests cover successful READ/WRITE recording, fixed event/resource classification, validation, authentication, authorization, query visibility, and chain verification. PostgreSQL-backed tests use the existing Testcontainers pattern. The fast authorization tests run without Docker.

## Final Clarification Decisions

### C-Q-01 — Access means both READ and WRITE client-account access

The source describes an access/activity context without narrowing the operation. Supporting the two existing CRUD-relevant access modes is the smallest useful interpretation. The API exposes `accessType` with only `READ` and `WRITE`.

### C-Q-02 — Record successful access only

Recording failed attempts would require observing decisions outside this service. The endpoint therefore records an explicitly submitted successful access event with `outcome: SUCCESS`; failed-access interception is out of scope.

### C-Q-03 — Client account data means a resource with `resourceType=CLIENT_ACCOUNT`

The existing event model already separates resource type and resource ID. The endpoint fixes the type and treats `resourceId` as the client-account identifier; no client-account table is introduced.

### C-Q-04 — Recording actors are SERVICE and ADMIN callers; `actorId` identifies the represented actor

These are the existing write-authorized authorities. No new actor taxonomy or authentication mechanism is justified.

### C-Q-05 — Use the fixed event type `CLIENT_ACCOUNT_ACCESS`

The existing `eventType` field is sufficient. A fixed value on the dedicated endpoint prevents callers from creating ambiguous Scenario C event types without adding a database constraint.

### C-Q-06 — No separate regulator or external-auditor audience is introduced

The repository provides no external audience requirement. Existing AUDITOR and ADMIN query access is reused, and no additional authority is added.

### C-Q-07 — Reuse Scenario B retention

No different retention period or legal hold requirement is stated. Scenario C records therefore follow the existing configurable retention behavior.

### C-Q-08 — Reuse Scenario B redaction

No different redaction policy is stated. Existing ADMIN-only structured redaction and its documented hash trade-off apply to Scenario C records.

### C-Q-09 — Use the existing query API for reporting

The existing `GET /api/v1/audit/events` endpoint already supports resource and actor filters, pagination, and deterministic ordering. No second reporting endpoint is required.

### C-Q-10 — Reuse optional inclusive `from` and `to` filters

The existing query contract already defines inclusive time-range filters. They remain optional because Scenario C does not state that every report must use a time range.

### C-Q-11 — Use existing actor/resource/event/time filters only

No Scenario C-specific filter is required by the source documents. The dedicated event and resource values make existing filters sufficient.

### C-Q-12 — Record access through the direct Scenario C API only

The endpoint is the explicit recording source. Existing export remains a read-only output and does not generate audit events; adding interceptors or report-side effects would expand scope.

## Clarification Decision Table

The table below records the initial decision space. The final decisions below supersede its provisional recommendation column and document the conservative assumptions used for implementation.

| ID | Question | Options | Recommended Option | Technical Impact | Decision Required |
|----|----------|---------|--------------------|------------------|------------------|
| C-Q-01 | What does "access" mean in this context — read access, write access, or both? | Read access; write access; both; another defined access operation | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines event or interception points. Database: may require access-operation fields. Security: may map to read/write permissions. Business logic: defines what is auditable. Testing: requires corresponding success and denial cases. | Define the access operations in scope. |
| C-Q-02 | Should successful access be recorded, failed access attempts, or both? | Successful only; failed only; both; another explicitly defined subset | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: controls response/event behavior. Database: may require outcome and failure-reason fields. Security: affects denied-request observability. Business logic: defines when an event is emitted. Testing: requires each selected outcome. | Select the outcomes that must be recorded. |
| C-Q-03 | What is the scope of "client account data"? | Specific resource types; all client account data; selected fields; another defined data boundary | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines resource validation and payload shape. Database: may require resource classification or indexes. Security: affects data exposure and redaction. Business logic: defines in-scope records. Testing: requires boundary and exclusion cases. | Define the resources and fields covered. |
| C-Q-04 | Who are the actors in this scenario? (end users, service accounts, administrators?) | End users; service accounts; administrators; combinations; another named actor set | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines accepted actor identity and provenance. Database: may require actor-type metadata. Security: determines authorities and access rules. Business logic: controls actor classification. Testing: requires authentication and authorization cases per actor. | Identify every actor category in scope. |
| C-Q-05 | What event types are required? | Existing event types; one or more new access event types; a defined event taxonomy | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines allowed eventType values. Database: may require no change or new constraints/indexes. Security: affects event visibility. Business logic: determines event construction. Testing: requires validation and hash-chain coverage for each type. | Approve the event taxonomy and required fields. |
| C-Q-06 | Is there a regulator or external auditor who requires visibility into this log? | No external audience; regulator; external auditor; multiple named audiences | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: may require a reporting/export interface. Database: may require audit views or scoped metadata. Security: may require new read authorities without bypassing authentication. Business logic: may require evidence-specific filtering. Testing: requires audience authorization and data-isolation tests. | Identify external audiences and their visibility obligations. |
| C-Q-07 | Does Scenario C have different retention requirements from Scenario A? | Reuse Scenario B retention; shorter period; longer period; legal hold or another policy | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: may require no endpoint or an administrative policy surface. Database: may require retention metadata or indexes. Security: affects administrative operations. Business logic: changes archival eligibility. Testing: requires boundary-time and verification behavior. | Define the retention policy and legal exceptions. |
| C-Q-08 | Does Scenario C have different redaction requirements? | Reuse Scenario B redaction; stricter rules; different fields; no redaction; another approved policy | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines redaction request and response behavior. Database: may require field metadata or hash-handling changes. Security: affects privileged access. Business logic: determines immutable evidence trade-offs. Testing: requires redaction and chain-verification cases. | Define whether and how Scenario C data may be redacted. |
| C-Q-09 | Who has reporting access and through which interface? | Existing query API; new report API; bulk export; named administrative or auditor roles; another interface | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines endpoints, filters, and response format. Database: determines query/index needs. Security: determines authorities and scope checks. Business logic: determines report assembly. Testing: requires role and data-scope tests. | Select the interface and reporting audience. |
| C-Q-10 | Is time-range filtering required for Scenario C reporting? | Required; not required; required only for named reports | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines query parameters and validation. Database: may reuse timestamp indexes or need new ones. Security: no direct change unless reports are separately scoped. Business logic: determines report boundaries. Testing: requires inclusive/exclusive boundary cases. | Decide whether time-range filtering is mandatory and define boundaries. |
| C-Q-11 | Are there additional filters specific to Scenario C? | Existing actor/resource/event filters; client-account filters; access-outcome filters; another defined set | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines request parameters. Database: may require columns or indexes. Security: may require row-scope enforcement. Business logic: determines filter combinations. Testing: requires individual, combined, invalid, and deterministic-order cases. | Define additional filters and their combination rules. |
| C-Q-12 | Must access be recorded via direct API calls, or also via exported reports? | Direct API calls only; exported reports only; both; another named source | INSUFFICIENT INFORMATION — DECISION REQUIRED | API: determines whether ingestion, reporting, or both are extended. Database: may require source metadata and duplicate-prevention rules. Security: determines where authorization is evaluated. Business logic: determines event timing and provenance. Testing: requires source-specific and duplicate scenarios. | Identify every recording source and its audit semantics. |

## Consolidated Implementation Impact

### API

No endpoint can be selected yet. Resolving the questions may require extending the existing event-ingestion API, adding a reporting endpoint, adding access-outcome filters to the existing query model, or adding a separate export/report contract. Request fields, response fields, status codes, and authorization rules remain undefined until the decisions are approved.

### Database

No database change is implied yet. Depending on the decisions, the existing `audit_event` columns may be sufficient, or a migration may be needed for access outcome, actor category, source, client-account scope, retention metadata, redaction metadata, constraints, or indexes. No table or column should be added before the normalized requirement identifies a data need.

### Business Logic

The future service behavior depends on whether events are emitted by direct API calls, security decisions, reports, or multiple sources. The final design may require access classification, outcome validation, actor and resource scope checks, report filtering, retention/redaction policy selection, duplicate handling, and transaction boundaries. None of these workflows is approved yet.

### Security

Existing JWT authentication and role-based authorization remain unchanged. Scenario C decisions must identify the actors, reporting audiences, and permissions before any new authority or endpoint rule is added. No external audience or privileged role is assumed.

### Testing

After decisions are approved, the test plan should include unit tests for event classification and validation; API tests for request and response contracts; database integration tests for persistence, filtering, retention, and redaction where applicable; security tests for authentication, authorization, and data scope; and regression tests for Scenario A and Scenario B. No Scenario C test can be made executable before the behavior is defined.

## Decisions Required From Vrushali

No additional decision is required for this implementation. C-Q-01 through C-Q-12 have been resolved using the documented decision policy and are recorded in the Final Clarification Decisions section. The assessment owner may revise these assumptions later if a more specific business contract is supplied.

## Limitations and Future Improvements

The remaining limitation is that PostgreSQL-backed verification requires Docker in the execution environment. The Scenario C decisions are conservative assumptions derived from the available assessment documents; they can be revised if the assessment owner supplies a more specific business contract.
