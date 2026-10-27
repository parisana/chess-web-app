# Chess Web App

A portfolio project built as a modular monolith: package-by-feature with conventional layers, keeping `Player` terminology. Cross-feature interactions use services. This project implements its own pure Java chess rules; AI is outside the MVP.

Reviewed on 27 October 2026 against `chess-web-app-day13-end.zip`. Source inspection confirms en passant candidate generation, immutable off-destination pawn removal, shared metadata handling and associated tests. No blocking defect was found in the reviewed Day 13 changes. The user reports all tests green, including the final additional tests. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Stack

- Backend: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok and YAML.
- Frontend: Angular 21, Angular Material, standalone components, reactive forms and in-memory authentication.

## Current capabilities

- Registration/login with validation, BCrypt and HS256 JWT authentication.
- Authenticated player profile GET and username PATCH, with an Angular profile editor.
- Persistent matchmaking queue, transactional pairing, active-player reservations, manual matched-game discovery and participant-protected game retrieval.
- Immutable chess representation, separate FEN parser/serializer and a `ChessRules.initialPosition()` boundary used by game creation.
- Strategy-based knight, rook, bishop, queen, ordinary king, ordinary pawn, explicit promotion and en passant candidates; shared sliding traversal, candidate application and king-safety filtering.
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

These are local commands, not a record of independently executed verification. The user reports all tests green for Day 13; the final Day 13 source has been inspected, and no separate frontend build result is recorded.

## Current boundaries

Movement is internal pure Java code and is not connected to `GameService` or HTTP. Games still store their initial FEN. Castling execution, complete legal-move enumeration, outcomes, move history, gameplay concurrency handling, clocks, real-time delivery and chessboard UI remain pending. Generation handles a single source and cannot establish checkmate or stalemate.

The parser validates structure, not full legality/reachability. Board uses identity equality; compare contents explicitly. Repetition requires history beyond FEN. Authorization, transactions, persistence and concurrency belong to services. Evaluating a 64-entry array while preserving the Board API remains deferred.

Queue pairing runs on join; the frontend refreshes manually. Games begin immediately in `IN_PROGRESS`, and active reservations have no release path yet. Logout does not leave the queue. Sessions are in memory.

## Documentation

- [Architecture](docs/architecture.md)
- [Development progress and verification](docs/development-progress.md)
- [Roadmap](docs/roadmap.md)
- [API contracts](docs/api-contracts.md)
- [Frontend setup and behavior](frontend/README.md)

## Day 13 completed checkpoint — 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.
