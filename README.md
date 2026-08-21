# Audit Log Service

Java 21 and Spring Boot audit-log service for the assessment. The service stores tamper-evident audit events in PostgreSQL, supports deterministic querying and hash-chain verification, and implements retention, structured redaction, bulk export, and client-account access recording.

## Technology Stack

- Java 21
- Spring Boot 3.3.4
- Maven Wrapper
- PostgreSQL 16
- Flyway migrations
- Spring JDBC
- OAuth2/JWT resource-server authentication
- JUnit, Spring Security Test, and Testcontainers

## Build

Use the Maven Wrapper from the repository root:

```text
./mvnw clean verify
```

The project requires Java 21. Java 25 is not supported by this assessment baseline.

## Run Locally

Start PostgreSQL with Docker Compose:

```text
docker compose up -d postgres
./mvnw spring-boot:run
```

The application reads database settings from `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. JWT discovery uses `JWT_JWK_SET_URI`. Do not commit real credentials or tokens.

## Testing

Run all tests with:

```text
./mvnw clean test
```

The PostgreSQL integration tests use Testcontainers and require a running Docker daemon. Unit, service, authentication, authorization, and context tests can run without Docker. The latest local testing evidence is recorded in [docs/TESTING-EVIDENCE.md](docs/TESTING-EVIDENCE.md); Docker-dependent tests are not reported as passed when Docker is unavailable.

GitHub Actions runs Java 21 and the Maven wrapper in `.github/workflows/ci.yml`. Its hosted runner provides Docker for Testcontainers.

## Security

The API is stateless and protected by OAuth2/JWT resource-server authentication. `SERVICE` and `ADMIN` authorities may write events, `AUDITOR` and `ADMIN` may query events, and administrative verification, retention, redaction, and export operations require `ADMIN`. Invalid or unauthorized requests return safe error responses without credentials, tokens, SQL, or stack traces.

## Repository Documentation

- [Requirement analysis](docs/REQUIREMENT-ANALYSIS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Database design](docs/DATABASE-DESIGN.md)
- [API contract](docs/API-CONTRACT.md)
- [Scenario C design](docs/SCENARIO-C-DESIGN.md)
- [Testing evidence](docs/TESTING-EVIDENCE.md)
- [AI usage log](ai/ai-usage.md)
