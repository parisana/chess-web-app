# Chess Web App

A backend-focused chess portfolio project built as a modular monolith with conventional feature layers. It implements its own pure Java rules engine and a minimal Angular interface.

## Current checkpoint

Day 27 replaces JWT authentication with persistent Spring Security sessions backed by PostgreSQL. Source-reviewed on 4 November 2026 against actual user chess-web-app-day27-end(1).zip. See [review](docs/day27-sessions-review.md), [session/WebSocket authentication](docs/session-authentication-flow.md), [detailed tab synchronization](docs/tab-synchronization-flow.md) and [handoff](docs/day27-handoff.md).

User reports the previously reported failures resolved and browser login/flow working. No assistant tests/builds/migrations/database/browser runs; detailed cross-tab/restart/deployment acceptance is not independently established. The shared test profile now disables timeout scanning and session cleanup. An intermittent game-action click observation remains deferred in the review.

**Session continuity is implemented.** Angular PGN download and post-MVP replay polish remain deferred.

## Capabilities

- New timed games start White with configured 10m/no increment; old games remain untimed.
- Versioned timeout completion freezes clocks, releases reservations and updates ratings atomically.
- Live countdowns remain live during replay; same-version GETs refresh clock estimates.

- Registration/login, BCrypt password handling, server-side session authentication and self-profile GET/username PATCH.
- Player Elo/statistics initialized at registration; all supported game completions update both Players atomically with duplicate protection.
- Session-derived self-statistics GET and a Home card with manual/live-hint/reconnect refresh.
- Persistent matchmaking with atomic pairing, oldest-first eligibility and one active-game reservation per Player.
- Immutable chess rules: all pieces, king safety, explicit promotion, en passant, standard castling and normal/check/checkmate/stalemate assessment.
- Participant-protected game GET and versioned HTTP move commands with optimistic concurrency protection.
- Atomic checkmate/stalemate, resignation and agreed-draw completion, persisted outcomes and release of both active reservations.
- One pending draw offer, opponent accept/decline and sender withdrawal; any accepted move clears the offer.
- Custom Player-oriented board, explicit promotion/cancellation and server-accepted FEN display.
- Ordered persisted moves with coordinates, promotion, resulting FEN, timestamps and SAN; explicit legacy-game baselines.
- Read-only replay: Start, Previous, Next, selectable history entries and explicit Latest return; live snapshots continue updating.
- Participant-protected `GET /api/v1/games/{gameId}/pgn` with UTF-8 PGN, result markers, custom-baseline support and legacy export rejection.
- Private live notifications, automatic queue/game reads, deferred reconciliation during commands, heartbeats and reconnect recovery.

**Pairing does not automatically navigate to the board.** Home updates to show the matched game; the Player clicks **Open game**. This is the intended Day 20 behavior.

## Stack

Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security and Spring Session JDBC, Lombok and YAML. Angular 21, Angular Material, signals, RxJS and `@stomp/stompjs` (declared `^7.5.0`).

## Run locally

Keep database, cookie and session configuration user-managed in `backend/src/main/resources/application.yaml`. Flyway migrations V1–V14 provide the schema; Hibernate validates it.

From `backend/`:

```bash
./gradlew bootRun
```

From `frontend/`, using compatible Node tooling and the declared npm 11.6.2:

```bash
npm ci
npm start
```

Open http://localhost:4200. The development proxy forwards `/api/**`, including WebSocket upgrades, to port 8080. Restart the dev server after proxy changes.

Backend WebSocket routing is configured under `app.websocket`; matching frontend addresses and timing are in `frontend/src/app/notification/config/realtime.config.ts`. See [configuration and protocol](docs/api-contracts.md#real-time-updates).

## Verification

Day 25 suggested focused commands, not assistant-executed results:

```bash
# No database:
./gradlew test --tests 'com.chessapp.player.rating.EloCalculatorTest' --tests 'com.chessapp.player.entity.PlayerStatisticsTest'

# Configured PostgreSQL test database:
./gradlew test --tests 'com.chessapp.player.PlayerStatisticsIntegrationTest' --tests 'com.chessapp.player.PlayerStatisticsHttpIntegrationTest' --tests 'com.chessapp.game.GameMigrationIntegrationTest'
```


Suggested commands, not assistant-executed results:

```bash
# From backend/, no database needed for these focused tests:
./gradlew test --tests 'com.chessapp.game.export.PgnFormatterTest' --tests 'com.chessapp.notification.config.WebSocketPropertiesTest' --tests 'com.chessapp.notification.security.*' --tests 'com.chessapp.game.service.GameMoveServiceTest' --tests 'com.chessapp.game.rules.notation.SanFormatterTest'

# Day 23 socket and migration checks require the configured PostgreSQL test database:
./gradlew test --tests 'com.chessapp.notification.WebSocketIntegrationTest' --tests 'com.chessapp.game.GameMigrationIntegrationTest'

# PGN HTTP checks, with the configured PostgreSQL test database:
./gradlew test --tests 'com.chessapp.game.GameReadIntegrationTest'

# With the configured PostgreSQL test database:
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

From `frontend/`:

```bash
npm test -- --watch=false
npm run build
```

Backend reports are generated under `backend/build/reports/tests/test/`. Frontend tests cover six auth-flow cases and three mocked tab-sync coordinator cases; this document does not establish their execution. The [Day 23 review](docs/day23-reliability-review.md) distinguishes inspected coverage, user-reported execution and remaining limits. Migration tests create and drop unique schemas and require schema creation permission. The broad notification test pattern now includes a database-backed socket test.

## Current limits

Notifications are best-effort live hints. Accepted moves are separately persisted in the database; notification delivery is not guaranteed. HTTP reads recover current state; commands are never automatically replayed. Angular restores its in-memory profile using the persisted session cookie after reload; session and cookie expiry still apply.

Existing games record forward from their migration-time FEN; earlier moves cannot be reconstructed. Entries recorded before SAN support retain null SAN, displayed as “—” beside their coordinates. Replay supports available legacy positions, but PGN requires complete history, contiguous records and SAN on every move. There is no Angular PGN download button, SAN backfill, repetition/move-count/dead-position draws, acceptance stage or abandonment policy. Closing the page, disconnecting or logging out does not directly finish a game or release reservations. Timed games continue counting and can later finish by TIMEOUT. Spectators, chat, AI, external brokers and distributed deployment remain outside current scope. The custom board has no legal-move highlighting, drag-and-drop or persistent check indicator. Promotion selection may appear before a proposed promotion is rejected for king safety; this UI improvement is intentionally deferred. The local profile/revision cache is distinct from PostgreSQL-persisted authentication sessions and games/history.

## Documentation

- [Day 27 review](docs/day27-sessions-review.md), [session and WebSocket authentication](docs/session-authentication-flow.md), [tab synchronization diagrams](docs/tab-synchronization-flow.md) and [handoff](docs/day27-handoff.md).

- [Day 26 clock review](docs/day26-clocks-review.md), [clock flow/pseudocode](docs/game-clocks-flow.md) and [handoff](docs/day26-handoff.md).

- [Day 25 source review](docs/day25-ratings-statistics-review.md), [Player statistics and Elo flow](docs/player-statistics-flow.md) and [next-session handoff](docs/day25-handoff.md).

- [Architecture](docs/architecture.md): current ownership and boundaries.
- [API and real-time contracts](docs/api-contracts.md).
- [Frontend gameplay flow](docs/frontend-gameplay-flow.md): Angular and board coordination.
- [Real-time flow](docs/realtime-updates-flow.md): diagrams for security, commit timing and recovery.
- [Development progress](docs/development-progress.md), [roadmap](docs/roadmap.md) and [frontend setup](frontend/README.md).
- [Movement pipeline](docs/movement-pipeline-architecture.md): pure rules companion.
- [Move history and SAN flow](docs/move-history-flow.md).
- [Replay and PGN flow](docs/replay-pgn-flow.md), [Day 24 source review](docs/day24-replay-pgn-review.md) and [next-session handoff](docs/day24-handoff.md).
- [Lifecycle flow](docs/game-lifecycle-flow.md), [Day 23 source review](docs/day23-reliability-review.md) and [historical Day 23 handoff](docs/day23-handoff.md).
- [Day 21 source review](docs/day21-history-review.md) retains historical checkpoint evidence.
- [Historical main-document checkpoints](docs/history/main-documents-through-day22.md). Existing Day documents retain their historical scope.

## Lifecycle policy

Either participant may resign or offer a draw regardless of turn. Only the recipient can accept/decline; only the sender can withdraw. Play continues during an offer, and any accepted move clears it. Resignation awards the opponent a win. Offers, responses and resignation advance the concurrency version without adding move-history rows. Angular confirms resignation/draw acceptance and shares one POST lock with moves. See [contracts](docs/api-contracts.md#explicit-lifecycle-commands).

## Rating policy and limits

Initial Elo is 1200, K=32 and scale=400; each completion applies one rounded delta oppositely. V11 excludes already finished games and initializes existing Players at zero counts; active games count when they later finish. No historical backfill, rating floor, provisional/rated modes, leaderboard, audit ledger or rating-aware pairing is implemented. Counts include zero-move resignation/agreement. The game-ID processing record prevents duplicate counting; it is not an event queue.

Statistics failures roll back completion; messaging failures after commit cannot. Home statistics use ordinary GETs and no polling. Missed hints can leave values stale until manual refresh, reconnection or reopening Home. Day 27 supplies session restoration; no access/refresh-token lifecycle remains in the current implementation.

## Clock policy and limits

Production uses app.game.clock initial-time=10m, timeout-scan-enabled=true and timeout-scan-interval=1s. Old games stay untimed. Exactly zero expires; no increment, pause, acceptance/grace stage or network-lag compensation exists. Current timeout policy always awards the opponent a win; full impossible-mate draw adjudication is deferred.

Commands after expiry receive 409 GAME_CLOCK_EXPIRED; the scanner completes games separately through normal transactional statistics/release. Read-only calculatedAt clock projections do not advance game version. Browser countdowns estimate elapsed time from response receipt and may lag delivery; only the backend declares outcomes. A 100-candidate scan batch is not a strict one-second guarantee. Shared test YAML now disables scans for fixture isolation. See the review for failure-starvation and coverage limits.

## Day 26 focused verification

Suggested commands only; no assistant execution:

```bash
# From backend/, no database for these suites:
./gradlew test --tests 'com.chessapp.game.clock.GameClockCalculatorTest' --tests 'com.chessapp.game.entity.GameClockEntityTest' --tests 'com.chessapp.game.config.GameClockPropertiesTest' --tests 'com.chessapp.game.dto.GameClockResponseTest' --tests 'com.chessapp.game.service.GameTimeoutScannerTest'

# With configured PostgreSQL test database:
./gradlew test --tests 'com.chessapp.game.GameClockPersistenceIntegrationTest' --tests 'com.chessapp.game.GameTimeoutIntegrationTest' --tests 'com.chessapp.game.GameMoveHttpIntegrationTest' --tests 'com.chessapp.game.GameMigrationIntegrationTest'
```

## Session settings

CHESS_SESSION is HttpOnly, path /, SameSite Lax, with configured 7d max-age. Server session inactivity is separately 7d. Cookie lifetime is not automatically renewed per request and no separate absolute server cap is implemented. SessionCookieConfig explicitly binds the YAML values. SESSION_COOKIE_SECURE defaults false for local HTTP; use true for HTTPS deployment. Flyway owns framework session tables; production expired-session cleanup runs every minute. Logout invalidates the shared session, while other independent browser sessions remain.

GET /api/v1/auth/csrf initializes the readable XSRF-TOKEN cookie. Angular sends X-XSRF-TOKEN on state-changing relative API requests. POST login returns the Player profile; POST logout returns 204. CONNECT uses the session-authenticated handshake plus a native CSRF header. See the flow companions for expiry, revocation, multi-tab limits and the cost of database access checks.
