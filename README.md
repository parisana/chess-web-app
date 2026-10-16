# Chess Web App

A portfolio project for building a real-time multiplayer chess application, with an emphasis on backend correctness, security, persistence, concurrency, and testing.

## Current status

Day 1 foundation is implemented: PostgreSQL persistence, Flyway V1/V2, player registration, password hashing, and initial unit, controller, and integration tests. Registration error handling and additional transaction tests remain. Login, JWT, matchmaking, the chess engine, WebSocket messaging, and the Angular application are planned rather than implemented.

See [development progress](docs/development-progress.md) for verified implementation details and remaining work, and [architecture](docs/architecture.md) for responsibilities and design decisions.

## Stack

| Area | Current choice |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.1, as declared in `backend/build.gradle` |
| Build | Gradle wrapper 9.8.1 |
| Persistence | PostgreSQL, Spring Data JPA, Flyway |
| Security | Spring Security; password hashing implemented, JWT planned |
| Configuration | YAML |
| Boilerplate | Lombok; no `@Data` on JPA entities |
| Frontend, planned | Angular and TypeScript |
| Real-time messaging, planned | WebSocket with STOMP |
| Tests | JUnit Jupiter, Mockito, Spring integration tests |

The backend is a modular monolith organized by feature, with conventional layers inside each feature. It does not adopt DDD. Redis, Kafka, and microservices are unnecessary for the initial scope.

## Repository layout

- `backend/`: Spring Boot application and Gradle wrapper.
- `frontend/`: placeholder for the planned Angular application.
- `docs/architecture.md`: architecture, boundaries, and current design gaps.
- `docs/development-progress.md`: implementation status, verification, and next steps.

## Local setup

Install Java 21 and PostgreSQL. Use the checked-in wrapper rather than a separately installed Gradle version. Commands below assume a Linux shell and start from the repository root.

Create the local development and test databases using a PostgreSQL administrator account:

```sql
CREATE USER chess_app WITH PASSWORD 'password';
CREATE DATABASE chess_db OWNER chess_app;
CREATE DATABASE chess_test_db OWNER chess_app;
```

These credentials match the current local defaults. The test database must remain separate from development data. Test startup applies Flyway migrations to the selected database.

The current shared configuration is `backend/src/main/resources/application.yaml`; `.yaml` and `.yml` are both YAML. Future examples use `.yml`. The development datasource accepts `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` overrides, with working local defaults. Environment variables are optional for local development.

For local overrides, create an ignored `application-local.yml` beside the shared configuration and activate `local`. Never commit real deployment credentials.

Start the backend:

```bash
cd backend
./gradlew --version
./gradlew bootRun
```

With local overrides:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## Registration API

`POST /api/v1/auth/register` is public and returns `201 Created` for valid new registrations.

```bash
curl -i http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"chessplayer1","email":"player1@example.com","password":"StrongPassword123!"}'
```

Response fields are `playerId`, `username`, and `email`. Passwords and hashes are never part of the response. Reusing this example creates a duplicate; a controlled `409 Conflict` response is still pending.

## Verification

From `backend/`, run the database-independent tests:

```bash
./gradlew test --tests com.chessapp.auth.service.AuthServiceTest --tests com.chessapp.auth.controller.AuthControllerTest
```

Run the complete suite against the test datasource, including the context-load test that currently lacks an explicit test profile:

```bash
SPRING_PROFILES_ACTIVE=test ./gradlew clean test
SPRING_PROFILES_ACTIVE=test ./gradlew test --rerun-tasks
```

The second command checks repeatability without accepting an up-to-date test result. HTML results are written to `backend/build/reports/tests/test/index.html`.

Do not assume plain `./gradlew test` isolates every Spring test yet: `ChessBackendApplicationTests` still needs `@ActiveProfiles("test")`. The registration integration test already activates that profile and rolls back its test transaction.

## Development approach

Work incrementally, explain engineering decisions, and verify each meaningful behavior. The next milestone is to finish Day 1 transaction coverage, then implement registration errors, a player service DTO, login, JWT, and authenticated API tests. See the progress document for the ordered checklist.
