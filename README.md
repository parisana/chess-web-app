# Chess Web App

Reviewed on 28 October 2026 against `chess-web-app-day16-end.zip`. Source inspection confirms the legality-enforcing facade, complete production strategy registration, facade regression tests and both [closing checks](docs/day16-closing-checks.md). No blocking defect was found in the reviewed Day 16 changes. The user reports all tests green through increments 1–2. Both later closing tests are present, but no subsequent explicit passing report was supplied for them. No tests or builds were run independently; no detailed test log or separate frontend build result was supplied.

The Day 16 section describes the inspected implementation and supersedes earlier initial-position-only boundary and test-only-construction statements. Historical sections retain the behavior and evidence available at those checkpoints.

## Day 16 current boundary

`ChessRules` exposes `initialPosition()`, `legalMoves(position)`, `applyMove(position, move)` and `evaluate(position)`. Lookup returns the complete immutable legal set. Application requires exact membership in that set, including the promotion choice, before delegating to `MoveApplier`. Assessment returns `PositionStatus` for the side to move. The existing pipeline remains shared; no per-piece application services or duplicate movement algorithms are introduced.

`GameService` still calls only `initialPosition()` and serializes it for creation. Games remain at their initial FEN in `IN_PROGRESS`. Move submission, persistence/history, gameplay concurrency, terminal lifecycle and active-reservation release remain pending. No endpoint, DTO, migration or frontend implementation changes in Day 16. Authorization, transactions, persistence and concurrency remain service responsibilities.

The rules facade is assembled in `game.config.GameRulesConfig`; rules code has no Spring/JPA/HTTP dependencies. See the architecture document for construction and the development-progress document for verification provenance.

A portfolio project built as a modular monolith: package-by-feature with conventional layers, keeping `Player` terminology. Cross-feature interactions use services. This project implements its own pure Java chess rules; AI is outside the MVP.

Historical Day 15 source-review provenance: reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

## Stack

- Backend: Java 21, Spring Boot 4.1.1, Gradle wrapper 9.8.1, PostgreSQL, JPA, Flyway, Spring Security/JWT, Lombok and YAML.
- Frontend: Angular 21, Angular Material, standalone components, reactive forms and in-memory authentication.

## Current capabilities

- Registration/login with validation, BCrypt and HS256 JWT authentication.
- Authenticated player profile GET and username PATCH, with an Angular profile editor.
- Persistent matchmaking queue, transactional pairing, active-player reservations, manual matched-game discovery and participant-protected game retrieval.
- Immutable chess representation, separate FEN parser/serializer and a rules facade for initial position, legal lookup/application and assessment; game creation uses its initial-position operation.
- Strategy-based knight, rook, bishop, queen, king (including standard castling), pawn, explicit promotion and en passant candidates; shared sliding traversal, candidate application and king-safety filtering.
- Position-wide legal-move enumeration and current-side normal/check/checkmate/stalemate assessment.
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

These are local commands, not independently executed verification. The user reports all tests green after the final Day 15 regressions. No detailed test log or separate frontend build result was supplied.

## Current limits

Repetition, move-count draws, dead-position detection, clocks, resignation and agreement are outside assessment. FEN is structural state; Board equality is identity-based. AI and map-versus-array evaluation remain deferred. Pairing runs on join with manual frontend refresh; sessions remain in memory. Logout does not leave the queue.

## Documentation

- [Architecture](docs/architecture.md)
- [Development progress and verification](docs/development-progress.md)
- [Roadmap](docs/roadmap.md)
- [API contracts](docs/api-contracts.md)
- [Frontend setup and behavior](frontend/README.md)
- [Movement pipeline walkthrough](docs/movement-pipeline-architecture.md)
- [Day 17 handoff](docs/day17-handoff.md)
- [Day 16 closing checks](docs/day16-closing-checks.md)
- [Post-Day-16 source review and proposed integration scope](docs/post-day16-integration-proposal.md) — proposal only; gameplay implementation awaits a separate scope decision.

## Day 13 completed checkpoint — historical, 27 October 2026

En passant is implemented for both colors. Generation requires the correct source rank, one forward diagonal step, matching target metadata, an empty destination and an adjacent opposing pawn. An inconsistent or unavailable opportunity contributes no en passant candidate and does not suppress ordinary moves. Attempting an omitted capture fails exact candidate membership with `IllegalArgumentException`; no unsupported-en-passant exception remains.

`MoveApplier` validates candidate membership before identifying a pawn's diagonal move into an empty square as en passant. It relocates the pawn and removes the opposing pawn from `(destination.file, source.rank)` through immutable Board operations. `Board.removePiece()` requires a nonnull occupied square and performs placement only; callers own chess-rule validation. The original position remains unchanged.

Shared handling switches turns, resets the pawn halfmove clock, increments the fullmove number after Black, and expires the target. En passant preserves existing castling rights. Ordinary moves, promotion choices and irreversible king/rook rights removal remain supported. An occupied diagonal follows ordinary capture rules even if inconsistent target metadata names it; no adjacent pawn is removed in that case. Promotion choices attached to en passant fail candidate membership.

`LegalMoveGenerator` remains shared and unchanged. It checks the moving side's king after both pawn squares have been vacated. Tests cover both colors, both capture directions in generation, FEN results, original-position preservation, missing/inconsistent metadata, absent/friendly/non-pawn adjacent pieces, wrong source ranks, occupied targets, opportunity expiry, ordinary advances instead of capture, castling-right preservation, invalid promotion choices, exposed rook/bishop attacks and removal of checking pawns. Board tests cover immutable removal and invalid removal inputs. The three obsolete unsupported-case tests were replaced. Selected tests use `com.chessapp.BoardTestRenderer`.

The user reports all tests green for the final Day 13 archive. Review was by source inspection; no independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` still stores the initial FEN and does not apply moves. Day 13 changes no HTTP contract, DTO, migration or frontend implementation. Castling execution, complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Generation handles a single source; an empty result cannot establish checkmate or stalemate. FEN parsing is structural, not proof of legality or reachability; the implementation does not reconstruct the preceding move from history. Board equality is identity-based, repetition requires history, and authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

## Day 14 implementation — historical, 27 October 2026

Standard castling is implemented for both colors and both sides. Candidates require the matching castling right, the king on e1/e8, a same-color rook on its original corner, and an empty intervening path. Inconsistent rights/placement omit the affected candidate while ordinary moves remain available. Rights are historical metadata; local placement checks do not reconstruct move history or distinguish a promoted rook from an original rook.

`MoveApplier` validates exact candidate membership, relocates the king and rook through immutable Board operations, and reuses shared metadata handling. Castling increments the halfmove clock once, increments the fullmove number after Black, switches turns once, clears the en passant target and permanently removes both rights for the moving king's color. Promotion choices on castling are rejected through candidate membership.

`LegalMoveGenerator` identifies castling separately from path safety. It checks the starting square on the original board, then the transit square with the king relocated and the rook still at its corner. The existing shared safety check examines the destination on the completed castling board. `MoveApplier` intentionally does not check attacks. Pinned pieces still count as attackers. Queenside b1/b8 must be empty but may be attacked; an attacked rook does not itself prohibit castling.

Source-confirmed coverage includes all four relocations and resulting FENs, ordinary king moves alongside castling, individual/absent rights, every intervening square, missing/wrong-color/non-rook corner pieces, displaced kings, attacks on starting/transit/destination squares, pinned-knight attacks, attacked rooks and b-file squares, en passant expiry, promotion-choice rejection and original-position preservation. `KingMoveGeneratorTest` replaces its obsolete castling-exclusion expectation. `CastlingTest` contains ten parameterized test methods; selected cases use `com.chessapp.BoardTestRenderer`. Existing movement, promotion and en passant test sources are unchanged.

Verification: the user reported green tests through the king-safety increment. Final additional boundary tests are present, with no subsequent explicit passing report. No independent tests or builds were run.

Movement remains internal pure Java code. `ChessRules` exposes only `initialPosition()`; `GameService` creates and stores the initial FEN and does not apply moves. Complete legal-move enumeration, outcomes, move history and gameplay integration remain pending. Single-source generation cannot establish checkmate or stalemate. FEN parsing remains structural, Board equality remains identity-based, and repetition requires history. Authorization, transactions, persistence and gameplay concurrency remain service responsibilities. AI and map-versus-array evaluation remain deferred.

## Day 15 completed checkpoint — 27 October 2026

Reviewed on 27 October 2026 against `chess-web-app-day15-end.zip`. Source inspection confirms position-wide legal-move enumeration and `NORMAL` / `CHECK` / `CHECKMATE` / `STALEMATE` assessment for the side to move. No blocking defect was found in the reviewed Day 15 changes. The user reports all tests green after the final guided regressions. No independent tests or builds were run; no detailed test log or separate frontend build result was supplied.

Position-wide enumeration reuses the existing single-source filtering body. Position assessment examines the current king and the complete reply set; the persisted game lifecycle is unchanged. See the architecture and development-progress documents for responsibilities and inspected coverage.
