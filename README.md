# Chess Web App

A portfolio project built as a modular monolith: package-by-feature with conventional layers, keeping `Player` terminology. Cross-feature interactions use services. This project implements its own pure Java chess rules; AI is outside the MVP.

Reviewed on 27 October 2026 against `chess-web-app-day12-end.zip`. Source inspection confirms explicit promotion choices, candidate generation, immutable replacement, shared metadata handling, renderer updates and associated tests. No blocking defect was found in the reviewed Day 12 changes. The user reports all tests green. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Stack

- Backend: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok and YAML.
- Frontend: Angular 21, Angular Material, standalone components, reactive forms and in-memory authentication.

## Current capabilities

- Registration/login with validation, BCrypt and HS256 JWT authentication.
- Authenticated player profile GET and username PATCH, with an Angular profile editor.
- Persistent matchmaking queue, transactional pairing, active-player reservations, manual matched-game discovery and participant-protected game retrieval.
- Immutable chess representation, separate FEN parser/serializer and a `ChessRules.initialPosition()` boundary used by game creation.
- Strategy-based knight, rook, bishop, queen, ordinary king, ordinary pawn and explicit promotion candidates; shared sliding traversal, candidate application and king-safety filtering.
- Attack detection for all six piece types, including pinned attackers. Resulting-board king safety handles relocation and captures. Moving kings and moving/captured corner rooks remove affected castling rights permanently.

## Run locally

Use Java 21 and the checked-in Gradle wrapper. Review `backend/src/main/resources/application.yaml` for the configured PostgreSQL connection and JWT settings. Database/secret configuration remains user-managed. Flyway applies V1–V5; Hibernate validates the schema.

From `backend/`:

```bash
./gradlew bootRun
```

From `frontend/`, use the declared npm 11.6.2 and a compatible Node version:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. The npm start script enables the proxy forwarding `/api/**` to the backend on port 8080.

## Local verification

From `backend/`, focused rules tests require no database:

```bash
./gradlew test --tests 'com.chessapp.game.rules.*'
```

With the configured PostgreSQL test database available:

```bash
SPRING_PROFILES_ACTIVE=test ./gradlew test
```

Backend reports are generated at `backend/build/reports/tests/test/index.html`.

From `frontend/`, after dependency installation:

```bash
npm test -- --watch=false
npm run build
```

These are local commands, not a record of independently executed verification. The user reports all tests green for Day 12; the final Day 12 source has been inspected, and no separate frontend build result is recorded.

## Current boundaries

Movement is internal pure Java code and is not connected to `GameService` or HTTP. Games still store their initial FEN. En passant capture/castling execution, complete legal-move enumeration, outcomes, move history, gameplay concurrency handling, clocks, real-time delivery and chessboard UI remain pending. A supported-piece subset cannot establish checkmate or stalemate.

The parser validates structure, not full legality/reachability. Board uses identity equality; compare contents explicitly. Repetition requires history beyond FEN. Authorization, transactions, persistence and concurrency belong to services. Evaluating a 64-entry array while preserving the Board API remains deferred.

Queue pairing runs on join; the frontend refreshes manually. Games begin immediately in `IN_PROGRESS`, and active reservations have no release path yet. Logout does not leave the queue. Sessions are in memory.

## Documentation

- [Architecture](docs/architecture.md)
- [Development progress and verification](docs/development-progress.md)
- [Roadmap](docs/roadmap.md)
- [API contracts](docs/api-contracts.md)
- [Frontend setup and behavior](frontend/README.md)

## Day 12 completed checkpoint — 27 October 2026

Promotion is implemented for both colors, for quiet advances and captures, with explicit queen, rook, bishop and knight choices. Ordinary moves retain the two-argument constructor. No choice defaults to queen.

`Move` validates replacement-type shape. Position-dependent validity is established by exact pseudo-legal candidate membership, including the choice. `MoveApplier` preserves the pawn's color and applies the replacement through an immutable Board operation; `LegalMoveGenerator` checks the moving side's king on the resulting board.

Pawn halfmove resets, Black fullmove increments, turn changes, en passant expiry and captured-corner-rook castling-right removal remain shared. A promoted corner rook never grants castling rights. The test renderer lists promotion choices distinctly.

The user reports all tests green for the final Day 12 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 12 changes no HTTP contract, DTO, migration or frontend implementation. En passant capture execution, castling execution, complete legal-move enumeration, outcomes, history and gameplay integration remain pending. A source-specific result cannot establish checkmate or stalemate.
