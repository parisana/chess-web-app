# Chess Web App

A portfolio project built as a modular monolith: package-by-feature with conventional layers, keeping `Player` terminology. Cross-feature interactions use services. This project implements its own pure Java chess rules; AI is outside the MVP.

Reviewed on 26 October 2026 against `chess-web-app-day10-end.zip`. Source inspection confirms the Day 10 king-movement changes and associated tests. No blocking defect was found in the reviewed changes. The user reports all tests green for this archive. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Stack

- Backend: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok and YAML.
- Frontend: Angular 21, Angular Material, standalone components, reactive forms and in-memory authentication.

## Current capabilities

- Registration/login with validation, BCrypt and HS256 JWT authentication.
- Authenticated player profile GET and username PATCH, with an Angular profile editor.
- Persistent matchmaking queue, transactional pairing, active-player reservations, manual matched-game discovery and participant-protected game retrieval.
- Immutable chess representation, separate FEN parser/serializer and a `ChessRules.initialPosition()` boundary used by game creation.
- Strategy-based knight, rook, bishop, queen and ordinary king candidates; shared sliding traversal, candidate application and king-safety filtering.
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

These are local commands, not a record of independently executed verification. The user reports all tests green for the final Day 10 archive; no separate frontend build result was supplied.

## Current boundaries

Movement is internal pure Java code and is not connected to `GameService` or HTTP. Games still store their initial FEN. Pawn movement, promotion, en passant/castling execution, complete legal-move enumeration, outcomes, move history, gameplay concurrency handling, clocks, real-time delivery and chessboard UI remain pending. A supported-piece subset cannot establish checkmate or stalemate.

The parser validates structure, not full legality/reachability. Board uses identity equality; compare contents explicitly. Repetition requires history beyond FEN. Authorization, transactions, persistence and concurrency belong to services. Evaluating a 64-entry array while preserving the Board API remains deferred.

Queue pairing runs on join; the frontend refreshes manually. Games begin immediately in `IN_PROGRESS`, and active reservations have no release path yet. Logout does not leave the queue. Sessions are in memory.

## Documentation

- [Architecture](docs/architecture.md)
- [Development progress and verification](docs/development-progress.md)
- [Roadmap](docs/roadmap.md)
- [API contracts](docs/api-contracts.md)
- [Frontend setup and behavior](frontend/README.md)
