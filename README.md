# Chess Web App

A portfolio-quality real-time multiplayer chess application in development. The current backend supports registration, email/password login, JWT authentication, and current-player profile retrieval.

## Architecture and stack

Modular monolith, package by feature with conventional layers; not DDD. Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security, and Lombok. Configuration uses YAML. Angular, matchmaking, the chess engine, and WebSocket/STOMP messaging are planned.

- `backend/`: application, migrations, and tests.
- `docs/architecture.md`: responsibilities and implementation decisions.
- `docs/development-progress.md`: reviewed coverage, limitations, and next steps.

The Angular directory is not present in this Git archive because Git does not track empty directories.

## Local setup

Install Java 21 and PostgreSQL. Create databases using a PostgreSQL administrator account:

```sql
CREATE USER chess_app WITH PASSWORD 'password';
CREATE DATABASE chess_db OWNER chess_app;
CREATE DATABASE chess_test_db OWNER chess_app;
```

These match the local defaults. Keep development and test data separate. Flyway applies V1/V2; Hibernate validates mappings instead of creating the schema.

### Signing key: required closeout fix

The reviewed snapshot contains a signing secret in tracked `backend/src/main/resources/application.yaml`. Move it out of shared configuration and replace it with a newly generated local key; the archived value must be treated as disclosed. This documentation update does not modify application configuration.

Generate a replacement:

```bash
openssl rand -base64 32
```

Remove `app.jwt.secret` from shared YAML and put the new value in ignored `backend/src/main/resources/application-local.yml`:

```yaml
app:
  jwt:
    secret: "PASTE_NEW_BASE64_KEY"
```

Keep `issuer: chess-web-app` and `access-token-ttl: 15m` in shared configuration. The test profile has its own deliberately public test-only key. Do not use that key for a deployed application. A new local signing key invalidates existing locally issued tokens, so log in again.

Shared datasource defaults accept optional `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` overrides; environment variables are not required for local setup. Real deployment credentials belong outside tracked files.

From the repository root:

```bash
cd backend
./gradlew --version
./gradlew bootRun --args='--spring.profiles.active=local'
```

## API

| Method | Route | Authentication | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | 201; `playerId`, `username`, `email` |
| POST | `/api/v1/auth/login` | Public | 200; `accessToken`, `tokenType`, `expiresIn` |
| GET | `/api/v1/players/me` | Bearer JWT | 200; `id`, `username`, `email` |

Register:

```bash
curl -i http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"chessplayer1","email":"player1@example.com","password":"StrongPassword123!"}'
```

Log in:

```bash
curl -i http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"player1@example.com","password":"StrongPassword123!"}'
```

Use the returned token:

```bash
curl -i http://localhost:8080/api/v1/players/me \
  -H 'Authorization: Bearer PASTE_ACCESS_TOKEN'
```

Login uses exact stored email matching. No trimming or lowercasing is implemented. Login returns token fields only; it does not currently embed `PlayerResponse`. Passwords and hashes are never public response fields.

## Validation and errors

Registration requires a nonblank username of 3–30 Java string units, a valid nonblank email of at most 255, and a nonblank password of at least 8 Java string units and at most 72 UTF-8 bytes. Login does not apply registration's minimum password length, but does enforce the byte maximum.

| Failure | Status | Code/format |
| --- | --- | --- |
| Invalid request fields | 400 | Problem Detail with `VALIDATION_FAILED` and field/message entries |
| Duplicate username | 409 | `USERNAME_ALREADY_EXISTS` |
| Duplicate email | 409 | `EMAIL_ALREADY_EXISTS` |
| Unknown account / incorrect password | 401 | `INVALID_CREDENTIALS`; generic message |
| Missing or invalid bearer token | 401 | Spring Security bearer handling; not the MVC error contract |
| Token player no longer exists at `/players/me` | 401 | MVC response-status error |

## JWT behavior

HS256 tokens carry player ID in `sub`, plus issuer, issuance time, and expiration. They contain no username/email. The configured lifetime is 15 minutes with 60 seconds of validation clock tolerance. Signature, algorithm, issuer, required expiration, timestamp validity, and positive-Long subject are validated.

`/players/me` queries PostgreSQL for current data. Other protected endpoints do not automatically check player existence. Refresh tokens, server-side logout, revocation, and account disabling are not implemented. Login responses use `Cache-Control: no-store`.

## Verification

From `backend/`:

```bash
./gradlew test --tests com.chessapp.auth.service.AuthServiceTest --tests com.chessapp.auth.controller.AuthControllerTest
SPRING_PROFILES_ACTIVE=test ./gradlew clean test
SPRING_PROFILES_ACTIVE=test ./gradlew test --rerun-tasks
```

All current Spring integration classes explicitly activate `test`. PostgreSQL must be available. Reports appear in `build/reports/tests/test/index.html`.

Source and test presence were reviewed on 17 October 2026. Tests could not execute in the review environment because the Gradle download was blocked; its default Java runtime was 17. No passing build is claimed by this review.

Before committing:

```bash
git diff --check
git diff --cached --check
```
