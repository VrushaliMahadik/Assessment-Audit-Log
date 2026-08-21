# AI Usage Audit Log — Audit Log Service Assessment

**Engineer:** Vrushali Mahadik

---

**ID:** AI-001
**Date:** 2026-08-21
**Prompt:** [Exact historical prompt not recorded]
**Summary:** AI suggested Java 25 as the project Java version.
**Decision:** REJECT

---

**ID:** AI-003
**Date:** 2026-08-21
**Prompt:** Create the requirement analysis document for the Audit Log Service assessment.
**Summary:** Created `docs/REQUIREMENT-ANALYSIS.md` covering assessment overview, core problem, functional requirements, scenarios A/B/C, security, data, API, non-functional requirements, traceability, open decisions, risks, and definition of done.
**Decision:** ACCEPT

---

**ID:** AI-004
**Date:** 2026-08-21
**Prompt:** Create the architecture and system design document for the Audit Log Service assessment.
**Summary:** Created `docs/ARCHITECTURE.md` covering architecture goals, layered component design, audit event write flow, hash chain model, canonicalisation, concurrency, transaction boundaries, query/verification flows, Scenario B architecture, security and authorisation boundaries, error handling, observability, deployment, 17 architecture decisions, risk register, and requirement traceability.
**Decision:** ACCEPT

---

**ID:** AI-005
**Date:** 2026-08-21
**Prompt:** Create the database design document for the Audit Log Service assessment.
**Summary:** Created `docs/DATABASE-DESIGN.md` covering the proposed PostgreSQL schema for `audit_event`, primary key strategy, timestamp design, hash chain data model, canonicalisation implications, append-only constraints, indexing strategy, concurrency and ordering, transaction boundaries, retention/redaction/export design, verification support, security considerations, schema diagram, 15 database decisions, 10 risks, and requirement traceability.
**Decision:** ACCEPT

---

**ID:** AI-006
**Date:** 2026-08-21
**Prompt:** Create the API contract document for the Audit Log Service assessment.
**Summary:** Created `docs/API-CONTRACT.md` covering API design principles, base path, POST /audit/events, GET /audit/events (with filters and pagination), GET /audit/events/{id}, GET /audit/verify (with violationType contract), Scenario B retention/redaction/export proposals, Scenario C ambiguity, authentication and authorisation tables, error response contract, validation rules, idempotency, security considerations, requirement traceability, 15 API decisions, and review checklist.
**Decision:** ACCEPT

---

**ID:** AI-007
**Date:** 2026-08-21
**Prompt:** Implement OAuth2/JWT resource-server authentication for the Audit Log Service.
**Summary:** Added spring-boot-starter-web, spring-boot-starter-oauth2-resource-server, and spring-security-test dependencies to pom.xml. Created SecurityConfig with stateless JWT resource-server configuration (CSRF disabled, STATELESS session, BearerTokenAuthenticationEntryPoint for 401). Created application.properties with JWT_JWK_SET_URI environment-variable-driven JWK set URI. Created AuthenticationIntegrationTest covering 5 cases: unauthenticated (401), invalid token (401), valid JWT (passes auth, 404 for unimplemented endpoint), principal present, and context loads. Updated AuditLogApplicationTests with @MockBean JwtDecoder. All 6 tests pass — BUILD SUCCESS.
**Decision:** ACCEPT

---

**ID:** AI-008
**Date:** 2026-08-21
**Prompt:** Implement role-based authorization for the Audit Log Service.
**Summary:** Updated SecurityConfig with endpoint-level authorization rules (SERVICE+ADMIN for POST /audit/events; AUDITOR+ADMIN for GET /audit/events and /audit/events/**; ADMIN only for GET /audit/verify; anyRequest().authenticated() fallback). Added JwtAuthenticationConverter reading authority names from JWT 'roles' claim with no ROLE_ prefix — use hasAuthority("ADMIN") convention. Added BearerTokenAccessDeniedHandler for 403 responses. Created AuthorizationIntegrationTest with 9 tests covering 401 (no JWT, invalid JWT), 403 (AUDITOR on verify, SERVICE on read, AUDITOR on create), allowed access (ADMIN create, AUDITOR read, SERVICE create), and Step 8 regression. Updated two existing AuthenticationIntegrationTest cases to include AUDITOR authority after role rules were applied to the tested endpoint. All 15 tests pass — BUILD SUCCESS.
**Decision:** ACCEPT

---

**ID:** AI-009
**Date:** 2026-08-21
**Prompt:** Create the actual PostgreSQL schema, required audit tables, migrations, and database integration setup for the Audit Log Service.
**Summary:** Added spring-boot-starter-jdbc, postgresql driver, flyway-core, flyway-database-postgresql, H2 (test), spring-boot-testcontainers, and testcontainers:postgresql/junit-jupiter to pom.xml. Created docker-compose.yml for local PostgreSQL (environment-variable-driven credentials). Created Flyway migration V1__create_audit_event_table.sql resolving DB-05 (UUID primary key) and DB-10 (sequence_number BIGSERIAL chain ordering). Updated application.properties with datasource and Flyway config. Created src/test/resources/application.properties with H2 + Flyway disabled for auth/security tests. Created TestcontainersConfiguration (shared, for future use). Created DatabaseMigrationIntegrationTest with @Testcontainers(disabledWithoutDocker=true) + @DynamicPropertySource covering 9 schema verification tests. All 15 existing tests pass; 9 DB migration tests skip gracefully when Docker is unavailable — BUILD SUCCESS.
**Decision:** ACCEPT

---

**ID:** AI-010
**Date:** 2026-08-21
**Prompt:** Implement Scenario A audit-log core functionality including event creation, persistence, querying, pagination, hash-chain verification, and tamper detection.
**Summary:** Added spring-boot-starter-validation. Created AuditEvent domain model, CreateAuditEventRequest (validated), AuditEventResponse, AuditEventPageResponse, VerifyChainResponse, AuditEventFilter DTOs. Created HashService with SHA-256, canonical sorted-key JSON (resolving OD-02/OD-03), genesis value 64-zeros (OD-04). Created AuditEventRepository (JDBC/JdbcTemplate) with dynamic filter queries, chain-ordering by sequence_number. Created AuditEventService with pg_advisory_xact_lock concurrency strategy (OD-06 resolved), @Transactional create/verify. Created AuditEventController (POST /api/v1/audit/events, GET /events, GET /events/{id}, GET /verify) and GlobalExceptionHandler. Updated AuthenticationIntegrationTest and AuthorizationIntegrationTest to mock AuditEventService and correct expected status codes. Created HashServiceTest (9 unit tests, always run) and ScenarioAIntegrationTest (23 integration tests, skip without Docker). Results: 24 tests PASS, 32 SKIPPED (Testcontainers), 0 FAIL — BUILD SUCCESS.
**Decision:** ACCEPT

---

## Template for New Entries

**ID:** AI-XXX
**Date:** YYYY-MM-DD
**Prompt:** Exact prompt used
**Summary:** What the AI produced
**Decision:** ACCEPT / MODIFY / REJECT
