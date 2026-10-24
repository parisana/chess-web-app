# Chess Web App

A portfolio-quality multiplayer chess application in development.
The backend supports registration, email/password login, JWT authentication,
current-player profiles and username updates, persistent matchmaking, transactional pairing,
and participant-protected game retrieval.

The Angular frontend supports authentication, username editing, queue controls,
and matched-game discovery through manual refresh.

## Architecture and stack

Modular monolith, package by feature with conventional layers; not DDD. Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security, and Lombok. Configuration uses YAML. Angular 21 and Angular Material provide registration/login and a protected home page. Persistent matchmaking and initial game state are implemented.
Knight-only generation, candidate application and king-safety filtering exist as pure Java components. Complete legal gameplay, gameplay UI and real-time notifications remain planned.

- `backend/`: application, migrations, and tests.
- `frontend/`: Angular application, Material theme, development proxy, and minimal authentication tests.
- `docs/architecture.md`: responsibilities and implementation decisions.
- `docs/development-progress.md`: current implementation, verification, and limitations.
- `docs/roadmap.md`: original milestone crosswalk, completed working sessions and proposed next scope.
- `docs/api-contracts.md`: endpoint requests, responses and expected error codes.

Checkpoint: Day 7 movement scope implemented, with fixture cleanup noted in the progress document. Reviewed on 24 October 2026 against `chess-web-app-day7-end.zip` (archive commit `323715a1a49cb03867bcfd0b20096436f04d744f`). The user reports that all tests are green for this Day 7 checkpoint. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied. Fixture corrections recorded in the development-progress document have not been applied or rerun as part of this review.

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

Verification provenance: Reviewed on 24 October 2026 against `chess-web-app-day7-end.zip` (archive commit `323715a1a49cb03867bcfd0b20096436f04d744f`). The user reports that all tests are green for this Day 7 checkpoint. This review inspected source and documentation only; no independent tests or builds were run. Separate frontend build/manual-check results were not supplied. Fixture corrections recorded in the development-progress document have not been applied or rerun as part of this review.

## Profile editing

`PATCH /api/v1/players/me` accepts `{ "username": "new_username" }` and updates only the JWT-authenticated player. The username must be nonblank and 3–30 Java UTF-16 string units long. Matching remains exact and case-sensitive; spaces and special characters are permitted. The same username is a successful no-op. Duplicate usernames return 409 with `USERNAME_ALREADY_EXISTS`; invalid fields return 400 with `VALIDATION_FAILED`.

The response is `PlayerResponse` (`id`, `username`, `email`). The Angular editor updates the in-memory player from that response, immediately changing the welcome message. Failed saves preserve input. Email, password and player ID are unchanged. Existing JWTs remain valid because their subject is the player ID. No migration is required. See [API contracts](docs/api-contracts.md) for action-specific errors.

## Matchmaking and game behavior

Queue status is `NOT_QUEUED`, `WAITING`, or `MATCHED`. Only `WAITING` includes an enqueue timestamp; `MATCHED` includes a game ID. Repeated joins preserve waiting time or return the existing active game. Pairing runs when a player joins and matches the oldest eligible entries, with random colors.

Game creation, reservations for both players, and removal of both queue entries share one transaction. A transaction-level PostgreSQL advisory lock serializes cooperating operations on the single queue. The reservation table independently enforces one active game per player.

Games start `IN_PROGRESS` with the initial FEN. Game retrieval returns `id`, `whitePlayerId`, `blackPlayerId`, `status`, `currentFen`, and `createdAt`. Either participant may read; a nonparticipant receives 403 and a missing game receives 404. The frontend currently displays the matched game ID; it has no game page or chessboard.

Leaving the queue does not cancel a game. Logout does not remove queue membership. No expiry, acceptance, cancellation, completion, reservation release, complete legal gameplay, move history, or notifications are implemented.

## Frontend session behavior

Login stores the token in memory, calls `/api/v1/players/me`, and marks the session authenticated only after loading the player. Logout and login/profile errors clear session state. Reloading the page loses the session. The interceptor attaches bearer tokens only to relative `/api/` requests, excluding login/register, and clears the matching session on protected-request 401 responses. It redirects established sessions to login; 403 responses do not clear them.

Failed login and registration submissions retain passwords. Login clears its password only on success; registration resets its form only on success. The backend remains authoritative for validation, including BCrypt's UTF-8 byte limit.

Before committing:

```bash
git diff --check
git diff --cached --check
```

## Day 6 rules foundation

The source contains pure Java `game.rules` and `game.rules.model` code: `PieceColor`, `PieceType`, immutable `Piece` and `Square` records, an immutable `Board`, `CastlingRights`, and `ChessPosition`. Coordinates run from a1=(0,0) to h8=(7,7). Board owns placement and defensively copies its map with `Map.copyOf`; Piece holds color and type only.

`ChessPosition` carries board, side to move, castling rights, optional en passant target, halfmove clock and fullmove number. `FenParser` validates FEN structure and representation constraints, not full legality or reachability. Separate `FenSerializer` emits canonical FEN.

`ChessRules` currently exposes only `initialPosition()`. `StandardChessRules` uses the parser and a private starting-FEN constant. `GameRulesConfig` wires the beans. `GameService` obtains the initial position through `ChessRules`, serializes it and passes nonblank initial FEN to `Game`; the entity no longer owns the starting-position constant. Rules/model code has no Spring, JPA, HTTP or persistence dependencies.

Focused tests cover square conversion, board immutability, position metadata, FEN parsing/serialization/round trips and standard initial placement. Game creation/read integration assertions check the expected initial FEN. Test presence is a source finding; passing results are user-reported.

Day 6 changes no migration, endpoint or frontend behavior. That historical Day 6 checkpoint had no movement pipeline. Day 7 adds the knight-only pipeline described below; special-move execution, outcomes and move history remain absent.

## Day 7 movement pipeline

Implemented in the reviewed checkpoint:

- `Move` is an immutable source/destination value with nonnull, distinct squares. It represents a proposal, not proof of legality; promotion is not represented yet.
- `Board.movePiece()` creates a new placement, replacing a captured destination piece and preserving the original. It performs placement checks only, not chess-movement validation.
- `KnightMoveGenerator` enumerates the eight knight offsets, clips board boundaries, respects side to move, excludes friendly destinations and king capture, and returns an immutable set. Empty sources and opposing knights produce an empty set; non-knight sources are rejected.
- `MoveApplier` currently accepts pseudo-legal knight candidates only. It updates placement, side to move, en passant expiry, halfmove/fullmove counters and castling rights affected by a captured corner rook. It does not check king safety. Counter increments reject integer overflow.
- `SquareAttackDetector` recognizes pawn, knight, king, rook, bishop and queen attacks from either color, independent of side to move. Sliding rays stop at the first occupied square; pinned pieces still count as attackers.
- `LegalMoveGenerator` requires exactly one king of each color, applies candidates and rejects those leaving the moving side's king attacked. It handles a single knight source; it assumes an otherwise valid standard-chess position rather than validating full legality/reachability.
- Shared `MoveApplier` and `LegalMoveGenerator` replace the knight-specific application/filtering classes. Both still depend on `KnightMoveGenerator`; dispatch for other pieces is deferred until a second movement type is introduced.
- `com.chessapp.BoardTestRenderer` lives in test sources. It marks inspected/source squares and supplied destinations, prints sorted candidates and can be included in assertion failure messages. It does not generate moves. `Candidates: (none)` means no moves were supplied to the renderer, not a game outcome.

Tests cover board relocation/capture immutability, knight offsets/edges/occupancy/turn selection, candidate metadata and castling-right changes, attack types/blockers/pawn direction/pinned attacks, and king-safety filtering including checks answered by blocking or capture. Legal-move and attack tests use matching subpackages; other tests remain in their existing locations.

Pseudo-legal moves follow movement and occupancy rules without establishing own-king safety. Legal moves additionally satisfy king safety and applicable special restrictions. This checkpoint generates only a knight subset: an empty result cannot establish checkmate or stalemate.

No new migration, endpoint, frontend behavior, move history or persisted gameplay command is introduced. `ChessRules` still exposes only `initialPosition()`; `GameService` does not call the movement pipeline.

See [development progress](docs/development-progress.md) for fixture corrections identified during review.
