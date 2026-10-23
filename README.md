# Chess Web App

A portfolio-quality multiplayer chess application in development.
The backend supports registration, email/password login, JWT authentication,
current-player profiles and username updates, persistent matchmaking, transactional pairing,
and participant-protected game retrieval.

The Angular frontend supports authentication, username editing, queue controls,
and matched-game discovery through manual refresh.

## Architecture and stack

Modular monolith, package by feature with conventional layers; not DDD. Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security, and Lombok. Configuration uses YAML. Angular 21 and Angular Material provide registration/login and a protected home page. Persistent matchmaking and initial game state are implemented.
Legal moves, gameplay UI, and real-time notifications remain planned.

- `backend/`: application, migrations, and tests.
- `frontend/`: Angular application, Material theme, development proxy, and minimal authentication tests.
- `docs/architecture.md`: responsibilities and implementation decisions.
- `docs/development-progress.md`: current implementation, verification, and limitations.
- `docs/roadmap.md`: original milestone crosswalk, agreed Day 5 scope and next planning target.
- `docs/api-contracts.md`: endpoint requests, responses and expected error codes.

Checkpoint: Day 5 username-update implementation recorded on 23 October 2026. The reviewed source baseline is the completed Day 4 archive `chess-web-app-day5-start.zip` (commit `3817be2637a043ce261cea31609f0f2b6b924acc`); Day 5 changes are documented from this guided implementation session. No post-Day-5 source archive has been reviewed. See `docs/development-progress.md` for verification provenance and `docs/roadmap.md` for scheduling.

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

## API

| Method | Route | Authentication | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | Public | 201; `playerId`, `username`, `email` |
| POST | `/api/v1/auth/login` | Public | 200; `accessToken`, `tokenType`, `expiresIn` |
| GET | `/api/v1/players/me` | Bearer JWT | 200; `id`, `username`, `email` |
| PATCH | `/api/v1/players/me` | Bearer JWT | 200; updated `id`, `username`, `email` |
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

`/players/me` queries PostgreSQL for current data. Matchmaking operations also check player existence. Game reads check the authenticated identity against the stored participants; JWT validation itself does not query the player table. Refresh tokens, server-side logout, revocation, and account disabling are not implemented. Login responses use `Cache-Control: no-store`.

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

Five focused authentication tests are present in `auth-flow.spec.ts`. There is no `app.spec.ts` in this checkpoint. Frontend coverage remains intentionally minimal; see the progress document for backend integration coverage.

Verification provenance: the user reported passing tests for Day 4 and confirmed completion of the Day 5 backend integration-test step. A detailed Day 5 test/build result log has not been supplied, and frontend build/manual-check outcomes have not been separately recorded. This documentation update reran no application tests or builds. Earlier review-environment failures are historical, not confirmed current blockers.

## Profile editing

`PATCH /api/v1/players/me` accepts `{ "username": "new_username" }` and updates only the JWT-authenticated player. The username must be nonblank and 3–30 Java UTF-16 string units long. Matching remains exact and case-sensitive; spaces and special characters are permitted. The same username is a successful no-op. Duplicate usernames return 409 with `USERNAME_ALREADY_EXISTS`; invalid fields return 400 with `VALIDATION_FAILED`.

The response is `PlayerResponse` (`id`, `username`, `email`). The Angular editor updates the in-memory player from that response, immediately changing the welcome message. Failed saves preserve input. Email, password and player ID are unchanged. Existing JWTs remain valid because their subject is the player ID. No migration is required. See [API contracts](docs/api-contracts.md) for action-specific errors.

## Matchmaking and game behavior

Queue status is `NOT_QUEUED`, `WAITING`, or `MATCHED`. Only `WAITING` includes an enqueue timestamp; `MATCHED` includes a game ID. Repeated joins preserve waiting time or return the existing active game. Pairing runs when a player joins and matches the oldest eligible entries, with random colors.

Game creation, reservations for both players, and removal of both queue entries share one transaction. A transaction-level PostgreSQL advisory lock serializes cooperating operations on the single queue. The reservation table independently enforces one active game per player.

Games start `IN_PROGRESS` with the initial FEN. Game retrieval returns `id`, `whitePlayerId`, `blackPlayerId`, `status`, `currentFen`, and `createdAt`. Either participant may read; a nonparticipant receives 403 and a missing game receives 404. The frontend currently displays the matched game ID; it has no game page or chessboard.

Leaving the queue does not cancel a game. Logout does not remove queue membership. No expiry, acceptance, cancellation, completion, reservation release, legal moves, move history, or notifications are implemented.

## Frontend session behavior

Login stores the token in memory, calls `/api/v1/players/me`, and marks the session authenticated only after loading the player. Logout and login/profile errors clear session state. Reloading the page loses the session. The interceptor attaches bearer tokens only to relative `/api/` requests, excluding login/register, and clears the matching session on protected-request 401 responses. It redirects established sessions to login; 403 responses do not clear them.

Failed login and registration submissions retain passwords. Login clears its password only on success; registration resets its form only on success. The backend remains authoritative for validation, including BCrypt's UTF-8 byte limit.

Before committing:

```bash
git diff --check
git diff --cached --check
```
