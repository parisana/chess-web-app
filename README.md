# Chess Web App

A portfolio-quality multiplayer chess application in development.
The backend supports registration, email/password login, JWT authentication,
current-player profiles, persistent matchmaking, transactional pairing,
and participant-protected game retrieval.

The Angular frontend supports authentication, queue controls, and matched
game discovery through manual refresh.

## Architecture and stack

Modular monolith, package by feature with conventional layers; not DDD. Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security, and Lombok. Configuration uses YAML. Angular 21 and Angular Material provide registration/login and a protected home page. Persistent matchmaking and initial game state are implemented.
Legal moves, gameplay UI, and real-time notifications remain planned.

- `backend/`: application, migrations, and tests.
- `frontend/`: Angular application, Material theme, development proxy, and minimal authentication tests.
- `docs/architecture.md`: responsibilities and implementation decisions.
- `docs/development-progress.md`: reviewed coverage, limitations, and next steps.

Checkpoint reviewed: Day 3, 20 October 2026. See `docs/development-progress.md` for verification blockers and the proposed next backend milestone.

## Local setup

Install Java 21 and PostgreSQL. Create databases using a PostgreSQL administrator account:

```sql
CREATE USER chess_app WITH PASSWORD 'password';
CREATE DATABASE chess_db OWNER chess_app;
CREATE DATABASE chess_test_db OWNER chess_app;
```

These match the local defaults. Keep development and test data separate. Flyway applies V1-V5; Hibernate validates mappings instead of creating the schema.

Configuration remains user-managed. This review does not change secrets or require configuration cleanup before the next milestone. Shared datasource defaults accept optional `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` overrides.

From the repository root:

```bash
cd backend
./gradlew --version
./gradlew bootRun
```

### Frontend

Use a Node version accepted by the checked-in Angular packages (their engine range is `^20.19.0 || ^22.12.0 || >=24.0.0`). `package.json` declares npm 11.6.2.

From `frontend/`:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. `npm start` enables the checked-in proxy, forwarding `/api/**` to `http://localhost:8080`; Angular uses relative API URLs.

The review's Node 24.19.0/npm 11.9.0 environment rejected `npm ci`, reporting missing `@emnapi/core@1.11.3` and `@emnapi/runtime@1.11.3` lockfile entries. If reproduced with the project's declared npm version, run `npm install --package-lock-only`, review the lockfile diff, then retry `npm ci`. No dependency files were changed during this review.

## API

| Method | Route | Authentication | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | 201; `playerId`, `username`, `email` |
| POST | `/api/v1/auth/login` | Public | 200; `accessToken`, `tokenType`, `expiresIn` |
| GET | `/api/v1/players/me` | Bearer JWT | 200; `id`, `username`, `email` |
| POST | `/api/v1/matchmaking/queue` | Bearer JWT | 200; current queue status |
| GET | `/api/v1/matchmaking/queue/me` | Bearer JWT | 200; current queue status |
| DELETE | `/api/v1/matchmaking/queue/me` | Bearer JWT | 204 |
| GET | `/api/v1/games/{gameId}` | Participant bearer JWT | 200; game state |

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

HS256 tokens carry player ID in `sub`, plus issuer, issuance time, and expiration. They contain no username/email. The configured lifetime is 15 minutes with 60 seconds of validation clock tolerance. Signature, algorithm, issuer, required expiration, expiration/not-before validity, and positive-Long subject are validated.

`/players/me` queries PostgreSQL for current data. Other protected endpoints do not automatically check player existence. Refresh tokens, server-side logout, revocation, and account disabling are not implemented. Login responses use `Cache-Control: no-store`.

## Verification

From `backend/`:

```bash
./gradlew test --tests com.chessapp.auth.service.AuthServiceTest --tests com.chessapp.auth.controller.AuthControllerTest
SPRING_PROFILES_ACTIVE=test ./gradlew clean test
SPRING_PROFILES_ACTIVE=test ./gradlew test --rerun-tasks
```

All current Spring integration classes explicitly activate `test`. PostgreSQL must be available. Reports appear in `build/reports/tests/test/index.html`.

From `frontend/` after dependency installation:

```bash
npm test -- --watch=false --include='src/app/auth/services/auth-flow.spec.ts'
npm test -- --watch=false
npm run build
```

Five focused authentication tests are present. The full suite also contains a stale generated assertion in `app.spec.ts`: it expects a heading removed from `app.html`. Remove that obsolete title test; retain the app creation smoke test. See the progress document for coverage and limitations.

Review execution on 20 October 2026: `npm ci` failed before tests/build could run. `bash gradlew --version` failed while downloading Gradle because network access was unavailable; the installed Java runtime is 17 rather than the project's 21. No successful test run or build is claimed.

## Frontend session behavior

Login stores the token in memory, calls `/api/v1/players/me`, and marks the session authenticated only after loading the player. Logout and login/profile errors clear session state. Reloading the page loses the session. The interceptor attaches bearer tokens only to relative `/api/` requests, excluding login/register, and clears the matching session on protected-request 401 responses. It redirects established sessions to login; 403 responses do not clear them.

Failed login and registration submissions retain passwords. Login clears its password only on success; registration resets its form only on success. The backend remains authoritative for validation, including BCrypt's UTF-8 byte limit.

Before committing:

```bash
git diff --check
git diff --cached --check
```
