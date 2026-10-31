# Chess Web App

A backend-focused chess portfolio project built as a modular monolith with conventional feature layers. It implements its own pure Java rules engine and a minimal Angular interface.

## Current checkpoint

Day 23 adds backend reliability coverage for WebSocket property binding, real socket security/private delivery and prior-schema database upgrades. Source-reviewed on 31 October 2026 against the actual user `chess-web-app-day23-end.zip`, compared with `chess-web-app-day22-end(1).zip`. Production behavior, migrations, dependencies and frontend code are unchanged. Day 22 resignation/draw negotiation, Day 21 history/SAN and Day 20 real-time recovery remain in place.

The user reports the Day 23 tests passing after the socket fixture corrections. The two previously reported draw HTTP tests are present and source-reviewed. The assistant ran no tests, builds, migrations or browser checks. See [Day 23 review and coverage](docs/day23-reliability-review.md).

## Capabilities

- Registration/login, BCrypt password handling, JWT authentication and self-profile GET/username PATCH.
- Persistent matchmaking with atomic pairing, oldest-first eligibility and one active-game reservation per Player.
- Immutable chess rules: all pieces, king safety, explicit promotion, en passant, standard castling and normal/check/checkmate/stalemate assessment.
- Participant-protected game GET and versioned HTTP move commands with optimistic concurrency protection.
- Atomic checkmate/stalemate, resignation and agreed-draw completion, persisted outcomes and release of both active reservations.
- One pending draw offer, opponent accept/decline and sender withdrawal; any accepted move clears the offer.
- Custom Player-oriented board, explicit promotion/cancellation and server-accepted FEN display.
- Ordered persisted moves with coordinates, promotion, resulting FEN, timestamps and SAN; explicit legacy-game baselines.
- Private live notifications, automatic queue/game reads, deferred reconciliation during commands, heartbeats and reconnect recovery.

**Pairing does not automatically navigate to the board.** Home updates to show the matched game; the Player clicks **Open game**. This is the intended Day 20 behavior.

## Stack

Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok and YAML. Angular 21, Angular Material, signals, RxJS and `@stomp/stompjs` (declared `^7.5.0`).

## Run locally

Keep database and JWT configuration user-managed in `backend/src/main/resources/application.yaml`. Flyway migrations V1–V10 provide the schema; Hibernate validates it.

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

Suggested commands, not assistant-executed results:

```bash
# From backend/, no database needed for these focused tests:
./gradlew test --tests 'com.chessapp.notification.config.WebSocketPropertiesTest' --tests 'com.chessapp.notification.security.*' --tests 'com.chessapp.game.service.GameMoveServiceTest' --tests 'com.chessapp.game.rules.notation.SanFormatterTest'

# Day 23 socket and migration checks require the configured PostgreSQL test database:
./gradlew test --tests 'com.chessapp.notification.WebSocketIntegrationTest' --tests 'com.chessapp.game.GameMigrationIntegrationTest'

# With the configured PostgreSQL test database:
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

From `frontend/`:

```bash
npm test -- --watch=false
npm run build
```

Backend reports are generated under `backend/build/reports/tests/test/`. Frontend tests remain the existing focused authentication suite. The [Day 23 review](docs/day23-reliability-review.md) distinguishes inspected coverage, user-reported execution and remaining limits. Migration tests create and drop unique schemas and require schema creation permission. The broad notification test pattern now includes a database-backed socket test.

## Current limits

Notifications are best-effort live hints. Accepted moves are separately persisted in the database; notification delivery is not guaranteed. HTTP reads recover current state; commands are never automatically replayed. Sessions are in memory, so browser reload requires login again.

Existing games record forward from their migration-time FEN; earlier moves cannot be reconstructed. Entries recorded before SAN support retain null SAN, displayed as “—” beside their coordinates. There are no replay controls, PGN export, clocks, repetition/move-count/dead-position draws, acceptance stage or abandonment policy. Closing the page, disconnecting or logging out does not finish a game or release its reservations. Spectators, chat, AI, external brokers and distributed deployment remain outside current scope. The custom board has no legal-move highlighting, drag-and-drop or persistent check indicator.

## Documentation

- [Architecture](docs/architecture.md): current ownership and boundaries.
- [API and real-time contracts](docs/api-contracts.md).
- [Frontend gameplay flow](docs/frontend-gameplay-flow.md): Angular and board coordination.
- [Real-time flow](docs/realtime-updates-flow.md): diagrams for security, commit timing and recovery.
- [Development progress](docs/development-progress.md), [roadmap](docs/roadmap.md) and [frontend setup](frontend/README.md).
- [Movement pipeline](docs/movement-pipeline-architecture.md): pure rules companion.
- [Move history and SAN flow](docs/move-history-flow.md).
- [Lifecycle flow](docs/game-lifecycle-flow.md), [Day 23 source review](docs/day23-reliability-review.md) and [next-session handoff](docs/day23-handoff.md).
- [Day 21 source review](docs/day21-history-review.md) retains historical checkpoint evidence.
- [Historical main-document checkpoints](docs/history/main-documents-through-day22.md). Existing Day documents retain their historical scope.

## Lifecycle policy

Either participant may resign or offer a draw regardless of turn. Only the recipient can accept/decline; only the sender can withdraw. Play continues during an offer, and any accepted move clears it. Resignation awards the opponent a win. Offers, responses and resignation advance the concurrency version without adding move-history rows. Angular confirms resignation/draw acceptance and shares one POST lock with moves. See [contracts](docs/api-contracts.md#explicit-lifecycle-commands).
